package com.salon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.salon.entity.UserEntity;
import com.salon.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * PHASE 5 — email verification API integration tests: MockMvc → controller →
 * VerificationService → JPA → the real local SQL Server, same conventions as
 * {@link AuthApiIntegrationTest} (real commits, ordered, self-cleaning).
 *
 * Covered end-to-end: token issued at signup (shape + expiry + unverified)
 * and never leaked by any response · resend rotates the token with ONE
 * generic message (anti-enumeration, asserted by message equality) ·
 * verify succeeds once and consumes the token · the rotated-away old token,
 * the consumed token, unknown tokens and a missing param are all rejected
 * with 400 · an expired token is rejected AND cleared · an already-verified
 * account with an outstanding token gets idempotent success · resend for a
 * verified account issues no new token. The rate limit on resend (429 +
 * Retry-After) is pinned by RateLimitEndpointScopingTest (zeroed limiter);
 * integration runs use the raised test limits like every other suite.
 *
 * Self-cleaning: the @AfterAll hook deletes the ONE account this class
 * creates (fixed test email) — same guarantee as AuthApiIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // real commits, like production
class EmailVerificationApiIntegrationTest {

	private static final String TEST_EMAIL = "verifyphase@example.com";
	private static final String TEST_NAME = "Verify Phase";
	private static final String TEST_PASSWORD = "TestPassword123";

	private static final String INVALID_MSG =
			"This verification link is invalid or has already been used.";
	private static final String EXPIRED_MSG =
			"This verification link has expired. Please request a new one.";
	private static final String GENERIC_RESEND_MSG =
			"If that email belongs to an account that still needs verification, "
			+ "a new link has been sent. Check your inbox and spam folder.";

	private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");

	/** Captured across ordered tests: the token killed by the resend (order 2)
	 *  and the token consumed by the successful verify (order 4), so their
	 *  REPLAYS below are real regression probes, not stand-ins. */
	private static String rotatedAwayToken;
	private static String consumedToken;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	/** Fresh read of the account (no OSIV, no wrapping tx → no L1 cache). */
	private UserEntity reloadUser() {
		entityManager.clear();
		return userRepository.findByEmailIgnoreCase(TEST_EMAIL).orElseThrow();
	}

	// ---------- signup issues the token ----------

	@Test
	@Order(1)
	void signup_issuesUnverifiedAccountWithTokenAndExpiry_neverLeaksEither() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "%s",
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(TEST_NAME, TEST_EMAIL, TEST_PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verified").value(false))
				// SECURITY: neither the token nor its expiry may ever leave the API.
				.andExpect(jsonPath("$.verificationToken").doesNotExist())
				.andExpect(jsonPath("$.tokenExpiryDate").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		UserEntity user = reloadUser();
		org.junit.jupiter.api.Assertions.assertNotNull(user.getVerificationToken());
		org.junit.jupiter.api.Assertions.assertTrue(
				HEX64.matcher(user.getVerificationToken()).matches(),
				"token must be 64 lowercase hex chars");
		org.junit.jupiter.api.Assertions.assertNotNull(user.getTokenExpiryDate());
		org.junit.jupiter.api.Assertions.assertTrue(user.getTokenExpiryDate().isAfter(LocalDateTime.now()),
				"expiry must be in the future");
		org.junit.jupiter.api.Assertions.assertFalse(user.isEmailVerified());
	}

	// ---------- resend rotates ----------

	@Test
	@Order(2)
	void resend_unverifiedAccount_rotatesToken_andReturnsGenericMessage() throws Exception {
		String oldToken = reloadUser().getVerificationToken();
		rotatedAwayToken = oldToken;

		mockMvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s" }
						""".formatted(TEST_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(GENERIC_RESEND_MSG));

		UserEntity user = reloadUser();
		org.junit.jupiter.api.Assertions.assertNotEquals(oldToken, user.getVerificationToken(),
				"resend must rotate (invalidate) the previous link");
		org.junit.jupiter.api.Assertions.assertTrue(
				HEX64.matcher(user.getVerificationToken()).matches());
		org.junit.jupiter.api.Assertions.assertTrue(user.getTokenExpiryDate().isAfter(LocalDateTime.now()));
		org.junit.jupiter.api.Assertions.assertFalse(user.isEmailVerified());
	}

	@Test
	@Order(3)
	void verify_withRotatedAwayOldToken_returns400() throws Exception {
		// Order 2 rotated the token — the original signup link must be dead
		// even though it never expired and was never used.
		mockMvc.perform(get("/api/auth/verify").param("token", rotatedAwayToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(INVALID_MSG));
	}

	// ---------- verify: success + single-use consumption ----------

	@Test
	@Order(4)
	void verify_withCurrentToken_marksVerified_andClearsToken() throws Exception {
		String token = reloadUser().getVerificationToken();
		consumedToken = token;

		mockMvc.perform(get("/api/auth/verify").param("token", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(true))
				.andExpect(jsonPath("$.message").isString())
				// SECURITY: the token is never echoed back.
				.andExpect(jsonPath("$.token").doesNotExist());

		UserEntity user = reloadUser();
		org.junit.jupiter.api.Assertions.assertTrue(user.isEmailVerified());
		org.junit.jupiter.api.Assertions.assertNull(user.getVerificationToken(),
				"token must be consumed on success (single use)");
		org.junit.jupiter.api.Assertions.assertNull(user.getTokenExpiryDate());
	}

	@Test
	@Order(5)
	void verify_withConsumedToken_returns400() throws Exception {
		// Exact replay of order 4's link — single-use means SINGLE use.
		mockMvc.perform(get("/api/auth/verify").param("token", consumedToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(INVALID_MSG));
	}

	// ---------- login reflects the verified flag ----------

	@Test
	@Order(6)
	void login_afterVerification_reportsVerifiedTrue() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(TEST_EMAIL, TEST_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(true))
				.andExpect(jsonPath("$.verificationToken").doesNotExist());
	}

	// ---------- expired token ----------

	@Test
	@Order(7)
	void verify_expiredToken_returns400_andClearsToken() throws Exception {
		UserEntity user = reloadUser();
		user.setVerificationToken("a".repeat(64));
		user.setTokenExpiryDate(LocalDateTime.now().minusMinutes(1));
		userRepository.save(user);

		mockMvc.perform(get("/api/auth/verify").param("token", "a".repeat(64)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(EXPIRED_MSG));

		UserEntity after = reloadUser();
		org.junit.jupiter.api.Assertions.assertNull(after.getVerificationToken(),
				"an expired token must be cleared so it cannot be retried");
		org.junit.jupiter.api.Assertions.assertTrue(after.isEmailVerified());
	}

	// ---------- already-verified edge cases ----------

	@Test
	@Order(8)
	void verify_alreadyVerifiedAccountWithOutstandingToken_isIdempotentSuccess() throws Exception {
		// Simulates the salon flipping email_verified manually (the old
		// workflow) while a signup token was still outstanding.
		UserEntity user = reloadUser();
		user.setVerificationToken("b".repeat(64));
		user.setTokenExpiryDate(LocalDateTime.now().plusHours(1));
		userRepository.save(user);

		mockMvc.perform(get("/api/auth/verify").param("token", "b".repeat(64)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(true))
				.andExpect(jsonPath("$.message").value("This email is already verified."));

		org.junit.jupiter.api.Assertions.assertNull(reloadUser().getVerificationToken(),
				"the outstanding token must be consumed either way");
	}

	@Test
	@Order(9)
	void resend_forVerifiedAccount_issuesNoNewToken_stillGenericMessage() throws Exception {
		mockMvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s" }
						""".formatted(TEST_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(GENERIC_RESEND_MSG));

		org.junit.jupiter.api.Assertions.assertNull(reloadUser().getVerificationToken(),
				"a verified account must not get a new token");
	}

	// ---------- anti-enumeration ----------

	@Test
	@Order(10)
	void resend_unknownEmail_returnsByteIdenticalMessage_noEnumeration() throws Exception {
		// Same body as order 9 (known, verified) — an attacker must not be
		// able to tell existing accounts from empty ones by the response.
		String known = mockMvc.perform(post("/api/auth/resend-verification")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s" }
								""".formatted(TEST_EMAIL)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String unknown = mockMvc.perform(post("/api/auth/resend-verification")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "nobody-exists-here@example.com" }
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		org.junit.jupiter.api.Assertions.assertEquals(known, unknown,
				"responses must be byte-identical for existing and unknown emails");
	}

	@Test
	@Order(11)
	void resend_invalidEmail_returns400_fieldError() throws Exception {
		mockMvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "not-an-email" }
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.email").exists());
	}

	// ---------- verify input handling ----------

	@Test
	@Order(12)
	void verify_withoutTokenParam_returns400() throws Exception {
		mockMvc.perform(get("/api/auth/verify"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Order(13)
	void verify_unknownToken_returns400_genericMessage() throws Exception {
		mockMvc.perform(get("/api/auth/verify").param("token", "f".repeat(64)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(INVALID_MSG));
	}

	@Test
	@Order(14)
	void verify_blankToken_returns400_genericMessage() throws Exception {
		mockMvc.perform(get("/api/auth/verify").param("token", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(INVALID_MSG));
	}

	// ---------- cleanup ----------

	@AfterAll
	static void cleanupTestUser(@Autowired UserRepository userRepository,
			@Autowired EntityManager entityManager) {
		entityManager.clear();
		var user = userRepository.findByEmailIgnoreCase(TEST_EMAIL).orElse(null);
		if (user != null) {
			userRepository.delete(user);
			userRepository.flush();
			System.out.println("[EmailVerificationApiIntegrationTest] cleaned up test account "
					+ TEST_EMAIL);
		}
	}
}
