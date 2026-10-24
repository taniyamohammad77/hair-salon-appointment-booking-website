package com.salon.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.salon.dto.AuthDtos.AuthResponse;
import com.salon.dto.AuthDtos.LoginRequest;
import com.salon.dto.AuthDtos.SignupRequest;
import com.salon.dto.AuthDtos.UserResponse;
import com.salon.entity.UserEntity;
import com.salon.exception.ApiException;
import com.salon.repository.UserRepository;

/**
 * Auth use-cases (auth Phase 2): signup, login, current-user lookup.
 * PHASE 5: signup issues + dispatches an email-verification token via
 * {@link VerificationService}.
 *
 * SECURITY invariants:
 *  - Passwords are stored ONLY as BCrypt hashes (default strength 10);
 *    the plaintext never touches the entity, the DB or a response.
 *  - Emails are normalized exactly like BookingService does for booking
 *    emails: trim + lowercase (DB collation is case-insensitive, so
 *    UQ_users_email already blocks case-variant duplicates — this makes
 *    the stored form deterministic too).
 *  - Login failures are one generic 401 message for "unknown email" and
 *    "wrong password" alike — no account enumeration.
 */
@Service
public class AuthService {

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	private final UserRepository userRepository;
	private final TokenService tokenService;
	private final VerificationService verificationService;

	public AuthService(UserRepository userRepository, TokenService tokenService,
			VerificationService verificationService) {
		this.userRepository = userRepository;
		this.tokenService = tokenService;
		this.verificationService = verificationService;
	}

	/** Create an account. Duplicate email → 409 (project error shape).
	 *  PHASE 5: a verification token is stamped onto the entity before the
	 *  first save (one insert), then the link is dispatched AFTER the commit
	 *  so the emailed link is already valid when it arrives. Dispatch never
	 *  throws — a dead SMTP server must not fail the signup itself. */
	public UserResponse signup(SignupRequest request) {
		String email = normalize(request.email());
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("An account with this email already exists. Try logging in instead.");
		}
		UserEntity user = new UserEntity();
		user.setName(request.name().trim());
		user.setEmail(email);
		user.setPasswordHash(encoder.encode(request.password()));
		user.setCreatedAt(LocalDateTime.now());
		verificationService.issueToken(user);
		UserEntity saved = userRepository.save(user);
		verificationService.sendVerificationEmail(saved);
		return UserResponse.from(saved);
	}

	/** Verify credentials → bearer token + identity. Anything wrong → 401. */
	public AuthResponse login(LoginRequest request) {
		String email = normalize(request.email());
		UserEntity user = userRepository.findByEmailIgnoreCase(email).orElse(null);
		if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
			throw ApiException.unauthorized("Incorrect email or password.");
		}
		return new AuthResponse(tokenService.issue(user.getId()),
				user.getId(), user.getName(), user.getEmail(), user.isEmailVerified());
	}

	/** Resolve the Authorization: Bearer header → current user identity. */
	public UserResponse currentUser(String authorizationHeader) {
		return UserResponse.from(currentUserEntity(authorizationHeader));
	}

	/** PHASE 5A: full entity (verified flag + createdAt) for ownership
	 *  gating in BookingService. Same 401 contract as {@link #currentUser}. */
	public UserEntity currentUserEntity(String authorizationHeader) {
		String token = null;
		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			token = authorizationHeader.substring("Bearer ".length()).trim();
		}
		Long userId = tokenService.verify(token);
		return userRepository.findById(userId)
				.orElseThrow(() -> ApiException.unauthorized("This account no longer exists. Please log in again."));
	}

	// ---------- helpers ----------

	private String normalize(String email) {
		return email.trim().toLowerCase();
	}
}
