package com.salon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.salon.config.AuthProperties;
import com.salon.exception.ApiException;

/**
 * Unit tests for the minimal HMAC bearer token (auth Phase 2). No Spring,
 * no DB — verifies issue/verify roundtrip and every rejection path
 * (tampered, malformed, blank, expired) plus the fail-fast secret check.
 * The expired-token case is forged here with the SAME algorithm/secret the
 * service uses, so the expiry branch is exercised without waiting.
 */
class TokenServiceTest {

	/** 48 chars — passes AuthProperties' >= 32 fail-fast rule. */
	private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

	private final TokenService tokens = new TokenService(new AuthProperties(SECRET, 10080));

	@Test
	void issueThenVerify_returnsSameUserId() {
		Long userId = tokens.verify(tokens.issue(42L));
		assertThat(userId).isEqualTo(42L);
	}

	@Test
	void tamperedPayload_isRejected() {
		String token = tokens.issue(42L);
		String payloadB64 = token.substring(0, token.indexOf('.'));
		byte[] payload = Base64.getUrlDecoder().decode(payloadB64);
		payload[0] = (byte) (payload[0] + 1); // flip one byte → signature no longer matches
		String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
				+ token.substring(token.indexOf('.'));
		assertThatThrownBy(() -> tokens.verify(tampered))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getStatus().value()).isEqualTo(401));
	}

	@Test
	void garbageToken_isRejected() {
		assertThatThrownBy(() -> tokens.verify("not-a-token"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getStatus().value()).isEqualTo(401));
	}

	@Test
	void tokenWithoutSeparator_isRejected() {
		assertThatThrownBy(() -> tokens.verify("abcdef"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getStatus().value()).isEqualTo(401));
	}

	@Test
	void blankToken_isRejected() {
		assertThatThrownBy(() -> tokens.verify(null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getStatus().value()).isEqualTo(401));
		assertThatThrownBy(() -> tokens.verify("   "))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getStatus().value()).isEqualTo(401));
	}

	@Test
	void expiredToken_isRejected() throws Exception {
		// Forge a correctly-signed token whose expiry is already in the past.
		String payload = "42|" + (Instant.now().getEpochSecond() - 10);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String forged = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
				+ "." + Base64.getUrlEncoder().withoutPadding()
						.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		assertThatThrownBy(() -> tokens.verify(forged))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getStatus().value()).isEqualTo(401));
	}

	@Test
	void shortSecret_failsFast() {
		assertThatThrownBy(() -> new AuthProperties("too-short", 10080))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
