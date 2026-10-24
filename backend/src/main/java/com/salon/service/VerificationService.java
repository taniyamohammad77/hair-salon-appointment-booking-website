package com.salon.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.salon.dto.AuthDtos.MessageResponse;
import com.salon.dto.AuthDtos.VerificationResponse;
import com.salon.entity.UserEntity;
import com.salon.exception.ApiException;
import com.salon.repository.UserRepository;

/**
 * PHASE 5 — automated email verification (replaces the manual
 * {@code email_verified} flag flip in SQL).
 *
 * <p>Flow: signup issues a single-use CSPRNG token (32 random bytes → 64
 * hex chars, {@code SecureRandom}) with a 24-hour expiry and dispatches a
 * verification link; {@code GET /api/auth/verify?token=} consumes it and
 * sets {@code email_verified = 1}; {@code POST /api/auth/resend-verification}
 * rotates the token for an unverified account (rate-limited per IP by the
 * interceptor, 5/min by default).
 *
 * <p>SECURITY invariants:
 * <ul>
 *   <li>Verification tokens are generated from {@link SecureRandom} only —
 *       never predictable, never derived from user data, never returned by
 *       any API response (the entity's token column has no DTO mapping).</li>
 *   <li>A token is single-use: consumed on success or on expiry, so a leaked
 *       link cannot be replayed.</li>
 *   <li>The resend response is ONE generic message whether the email exists,
 *       is already verified, or is unknown — no account enumeration.</li>
 *   <li>Dispatch failures are logged, never propagated: a dead SMTP server
 *       must not fail a signup (the account exists) and must not produce
 *       different resend responses for known vs unknown emails (that would
 *       itself be an enumeration oracle).</li>
 * </ul>
 *
 * <p>Dispatch modes (checked per send): SMTP when {@code spring.mail.host}
 * is configured (standard Spring Boot {@code spring.mail.*} properties, e.g.
 * {@code SPRING_MAIL_HOST}/{@code SPRING_MAIL_USERNAME}/{@code SPRING_MAIL_PASSWORD}
 * env vars); otherwise a DEV-LOG fallback that writes the link to the backend
 * log — local flows stay fully testable with zero email credentials.
 */
@Service
public class VerificationService {

	private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

	/** CSPRNG token: 32 bytes → 64 lowercase hex chars (fits NVARCHAR(64)). */
	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	static final String INVALID_LINK_MSG =
			"This verification link is invalid or has already been used.";
	static final String EXPIRED_LINK_MSG =
			"This verification link has expired. Please request a new one.";
	/** Identical body for exists-unverified / exists-verified / unknown. */
	static final String GENERIC_RESEND_MSG =
			"If that email belongs to an account that still needs verification, "
			+ "a new link has been sent. Check your inbox and spam folder.";

	private final UserRepository userRepository;
	private final ObjectProvider<JavaMailSender> mailSender;
	private final long ttlMinutes;
	private final String verifyLinkBaseUrl;
	private final String fromAddress;
	/** Blank (the default) = SMTP not configured → dev-log dispatch mode. */
	private final String mailHost;

	public VerificationService(
			UserRepository userRepository,
			ObjectProvider<JavaMailSender> mailSender,
			@Value("${salon.auth.verification-ttl-minutes:1440}") long ttlMinutes,
			@Value("${salon.mail.verify-link-base-url:http://localhost:8081/verify.html}")
					String verifyLinkBaseUrl,
			@Value("${salon.mail.from:no-reply@amore-salon.example}") String fromAddress,
			@Value("${spring.mail.host:}") String mailHost) {
		this.userRepository = userRepository;
		this.mailSender = mailSender;
		this.ttlMinutes = ttlMinutes > 0 ? ttlMinutes : 1440;
		this.verifyLinkBaseUrl = verifyLinkBaseUrl;
		this.fromAddress = fromAddress;
		this.mailHost = mailHost;
	}

	/**
	 * PHASE 5 (signup): stamp a fresh token + expiry onto the entity BEFORE
	 * it is first saved — one insert, no follow-up update.
	 */
	public void issueToken(UserEntity user) {
		user.setVerificationToken(newToken());
		user.setTokenExpiryDate(LocalDateTime.now().plusMinutes(ttlMinutes));
	}

	/**
	 * Deliver the link for the token currently on the entity. NEVER throws —
	 * see class javadoc (dispatch failure must not fail signup or leak
	 * account existence through differing resend responses).
	 */
	public void sendVerificationEmail(UserEntity user) {
		String token = user.getVerificationToken();
		if (token == null) {
			return; // nothing to send (already verified / legacy account)
		}
		String link = verifyLinkBaseUrl
				+ (verifyLinkBaseUrl.contains("?") ? "&" : "?") + "token=" + token;
		if (mailHost == null || mailHost.isBlank()) {
			// DEV-LOG fallback: no SMTP configured (the local default).
			// The link lands in the backend log so the flow stays testable.
			log.info("[DEV MAIL] verification link for {}: {}", user.getEmail(), link);
			return;
		}
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			log.error("spring.mail.host is set but no JavaMailSender bean exists — "
					+ "verification email for {} NOT sent.", user.getEmail());
			return;
		}
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromAddress);
			message.setTo(user.getEmail());
			message.setSubject("Verify your email — Amoré");
			message.setText("Hi " + user.getName() + ",\n\n"
					+ "Please verify your email address by opening this link:\n"
					+ link + "\n\n"
					+ "The link expires in " + ttlMinutes / 60 + " hours and can be used once.\n"
					+ "If you didn't create an account with us, you can safely ignore this email.\n\n"
					+ "— Amoré Salon\n");
			sender.send(message);
			log.info("Verification email dispatched to {}", user.getEmail());
		} catch (Exception e) {
			// Honest server-side log; the API response stays generic on purpose.
			log.error("Verification email dispatch to {} failed: {}",
					user.getEmail(), e.getMessage());
		}
	}

	/**
	 * GET /api/auth/verify — consume the token. Outcomes: success (marks
	 * verified, clears the token) · idempotent success for an already
	 * verified account · 400 for unknown/used tokens · 400 for expired
	 * tokens (also cleared so they cannot be retried).
	 */
	public VerificationResponse verify(String token) {
		if (token == null || token.isBlank()) {
			throw ApiException.badRequest(INVALID_LINK_MSG);
		}
		UserEntity user = userRepository.findByVerificationToken(token.trim())
				.orElseThrow(() -> ApiException.badRequest(INVALID_LINK_MSG));

		LocalDateTime expiry = user.getTokenExpiryDate();
		if (expiry == null || expiry.isBefore(LocalDateTime.now())) {
			user.setVerificationToken(null);
			user.setTokenExpiryDate(null);
			userRepository.save(user);
			throw ApiException.badRequest(EXPIRED_LINK_MSG);
		}

		String message;
		if (user.isEmailVerified()) {
			// Edge case: the salon flipped the flag manually while a token
			// was still outstanding — idempotent success, just consume it.
			message = "This email is already verified.";
		} else {
			user.setEmailVerified(true);
			message = "Email verified — thank you! You can now log in and manage your bookings.";
		}
		user.setVerificationToken(null);
		user.setTokenExpiryDate(null);
		userRepository.save(user);
		return new VerificationResponse(true, message);
	}

	/**
	 * POST /api/auth/resend-verification — rotate the token for an
	 * EXISTING, UNVERIFIED account and dispatch again. Returns
	 * {@link #GENERIC_RESEND_MSG} unconditionally (anti-enumeration);
	 * known-verified and unknown addresses are no-ops.
	 */
	public MessageResponse resend(String email) {
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		userRepository.findByEmailIgnoreCase(normalized).ifPresent(user -> {
			if (!user.isEmailVerified()) {
				issueToken(user); // rotates: the previous link dies immediately
				userRepository.save(user);
				sendVerificationEmail(user);
			}
		});
		return new MessageResponse(GENERIC_RESEND_MSG);
	}

	/** Single-use CSPRNG token: 64 lowercase hex chars (256 bits). */
	static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
		}
		return sb.toString();
	}
}
