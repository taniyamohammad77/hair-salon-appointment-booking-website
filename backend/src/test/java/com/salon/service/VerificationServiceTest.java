package com.salon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * PHASE 5 unit tests — verification token generation (no Spring context, no
 * DB: newToken() is a pure CSPRNG helper). The DB-backed flows (issue →
 * verify → consume, expiry, resend rotation) are covered end-to-end by
 * {@code EmailVerificationApiIntegrationTest} against the real test database.
 */
class VerificationServiceTest {

	private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");

	@Test
	void newToken_is64LowercaseHexChars_fittingTheDbColumn() {
		// NVARCHAR(64) in schema.sql — a longer token would be truncated.
		String token = VerificationService.newToken();
		assertTrue(HEX64.matcher(token).matches(),
				"token must be exactly 64 lowercase hex chars, got: length=" + token.length());
	}

	@Test
	void newToken_isUniqueAcrossCalls() {
		// 256 bits of CSPRNG output: collisions are not realistically
		// reachable, and uniqueness is what keeps single-use consumption safe.
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			assertTrue(seen.add(VerificationService.newToken()), "duplicate token at iteration " + i);
		}
		assertEquals(100, seen.size());
	}

	@Test
	void newToken_differsFromEveryPriorPattern() {
		assertNotEquals(VerificationService.newToken(), VerificationService.newToken());
	}
}
