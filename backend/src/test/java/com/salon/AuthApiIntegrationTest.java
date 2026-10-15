package com.salon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.salon.config.AuthProperties;
import com.salon.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Auth API integration tests (auth Phase 2): MockMvc → controller →
 * services → JPA → the real local SQL Server, same conventions as
 * {@link BookingApiIntegrationTest} (real commits, ordered, self-cleaning).
 *
 * Self-cleaning: the @AfterAll hook deletes the ONE account this class
 * creates (fixed test email) — the users table stays empty between runs.
 * No user is inserted outside the API under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // real commits, like production
class AuthApiIntegrationTest {

	private static final String TEST_EMAIL = "authtest@example.com";
	private static final String TEST_NAME = "Auth Test";
	private static final String TEST_PASSWORD = "TestPassword123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AuthProperties authProperties;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	// ---------- signup ----------

	@Test
	@Order(1)
	void signup_returns201_normalizesEmail_neverReturnsSecrets() throws Exception {
		// Mixed-case email on purpose — response must be the normalized form.
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "%s",
						  "email": "AuthTest@Example.COM",
						  "password": "%s"
						}
						""".formatted(TEST_NAME, TEST_PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value(TEST_NAME))
				.andExpect(jsonPath("$.email").value(TEST_EMAIL))
				// SECURITY: neither plaintext password nor its hash may leak.
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	@Order(2)
	void signup_duplicateEmail_returns409() throws Exception {
		// Even the already-lowercase form must collide (UQ_users_email + check).
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "name": "Second Attempt",
						  "email": "%s",
						  "password": "AnotherPass456"
						}
						""").formatted(TEST_EMAIL)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("An account with this email already exists. Try logging in instead."));
	}

	@Test
	@Order(3)
	void signup_shortPassword_returns400() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "name": "Short Pass",
						  "email": "shortpass@example.com",
						  "password": "short7"
						}
						""")))
				.andExpect(status().isBadRequest())
				// The validation ladder surfaces the password rule via fields.
				.andExpect(jsonPath("$.fields.password").exists());
	}

	// ---------- login ----------

	@Test
	@Order(4)
	void login_wrongPassword_returns401() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "email": "%s",
						  "password": "WrongPassword999"
						}
						""").formatted(TEST_EMAIL)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Incorrect email or password."));
	}

	@Test
	@Order(5)
	void login_unknownEmail_returnsSame401() throws Exception {
		// Same generic message as wrong-password — no account enumeration.
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "nobody@example.com",
						  "password": "Whatever123"
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Incorrect email or password."));
	}

	@Test
	@Order(6)
	void login_returnsToken_andIdentity() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "email": "AuthTest@Example.COM",
						  "password": "%s"
						}
						""").formatted(TEST_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value(TEST_NAME))
				.andExpect(jsonPath("$.email").value(TEST_EMAIL))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	// ---------- /me ----------

	private String loginToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""").formatted(TEST_EMAIL, TEST_PASSWORD)))
				.andExpect(status().isOk()).andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return body.path("token").asString();
	}

	@Test
	@Order(7)
	void me_withValidToken_returnsIdentity() throws Exception {
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + loginToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(TEST_NAME))
				.andExpect(jsonPath("$.email").value(TEST_EMAIL))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	@Order(8)
	void me_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Authentication is required. Please log in."));
	}

	@Test
	@Order(9)
	void me_withGarbageToken_returns401() throws Exception {
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Order(10)
	void me_withTamperedSignature_returns401() throws Exception {
		String token = loginToken();
		int dot = token.indexOf('.');
		String tampered = token.substring(0, dot + 1)
				+ ('A' == token.charAt(token.length() - 1) ? 'B' : 'A')
				+ token.substring(dot + 2);
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Order(11)
	void me_withExpiredToken_returns401() throws Exception {
		// Forge a correctly-signed token with a past expiry using the REAL
		// configured secret — proves the expiry branch end-to-end.
		String payload = "1|" + (Instant.now().getEpochSecond() - 10);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(authProperties.tokenSecret().getBytes(), "HmacSHA256"));
		String expired = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes())
				+ "." + Base64.getUrlEncoder().withoutPadding()
						.encodeToString(mac.doFinal(payload.getBytes()));
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Your session has expired. Please log in again."));
	}

	// ---------- cleanup ----------

	@AfterAll
	static void cleanupTestUser(@Autowired UserRepository userRepository,
			@Autowired EntityManager entityManager) {
		// The account this class created via the signup API is removed so
		// every run starts clean (users table returns to 0 rows).
		entityManager.clear();
		var user = userRepository.findByEmailIgnoreCase(TEST_EMAIL).orElse(null);
		if (user != null) {
			userRepository.delete(user);
			userRepository.flush();
			System.out.println("[AuthApiIntegrationTest] cleaned up test account " + TEST_EMAIL);
		}
	}
}
