package com.salon.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.salon.config.AuthProperties;
import com.salon.exception.ApiException;

/**
 * Bearer-token issue/verify (auth Phase 2) — minimal HMAC-SHA256, JDK-only
 * (no JWT library, no Spring Security).
 *
 * Token shape:  base64url(payload) + "." + base64url(HMAC-SHA256(payload))
 * Payload:      "<userId>|<expiryEpochSeconds>"
 *
 * The token intentionally carries ONLY the user id + expiry — /api/auth/me
 * loads the user row, so name/email changes take effect immediately and no
 * stale identity is baked into the token. Signature comparison is
 * constant-time (MessageDigest.isEqual). A fresh Mac is created per
 * operation: javax.crypto.Mac is stateful and not thread-safe.
 */
@Service
public class TokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();

	private final SecretKeySpec keySpec;
	private final long ttlMinutes;

	public TokenService(AuthProperties properties) {
		this.keySpec = new SecretKeySpec(
				properties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
		this.ttlMinutes = properties.tokenTtlMinutes();
	}

	/** Issue a token for this user id. */
	public String issue(Long userId) {
		long expiry = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES).getEpochSecond();
		String payload = userId + "|" + expiry;
		return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
				+ "." + B64.encodeToString(hmac(payload));
	}

	/**
	 * Verify a token's signature + expiry and return its user id.
	 * EVERY failure — missing, malformed, wrong signature, expired — is the
	 * same honest 401; messages differ only to help the user recover.
	 */
	public Long verify(String token) {
		if (token == null || token.isBlank()) {
			throw ApiException.unauthorized("Authentication is required. Please log in.");
		}
		String[] parts = token.split("\\.", -1);
		if (parts.length != 2) {
			throw invalidToken();
		}
		byte[] payload;
		byte[] signature;
		try {
			payload = B64_DECODE.decode(parts[0]);
			signature = B64_DECODE.decode(parts[1]);
		} catch (IllegalArgumentException ex) {
			throw invalidToken(); // not valid base64url
		}
		byte[] expected = hmac(new String(payload, StandardCharsets.UTF_8));
		if (!MessageDigest.isEqual(signature, expected)) {
			throw invalidToken(); // wrong signature — also covers tampered payloads
		}
		String[] fields = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
		if (fields.length != 2) {
			throw invalidToken();
		}
		long expiry;
		try {
			expiry = Long.parseLong(fields[1]);
		} catch (NumberFormatException ex) {
			throw invalidToken();
		}
		if (Instant.now().getEpochSecond() >= expiry) {
			throw ApiException.unauthorized("Your session has expired. Please log in again.");
		}
		try {
			return Long.parseLong(fields[0]);
		} catch (NumberFormatException ex) {
			throw invalidToken();
		}
	}

	// ---------- helpers ----------

	private byte[] hmac(String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(keySpec);
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ex) {
			// JVM cannot do HMAC-SHA256 — unrecoverable configuration problem.
			throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
		}
	}

	private ApiException invalidToken() {
		return ApiException.unauthorized("Your session is invalid. Please log in again.");
	}
}
