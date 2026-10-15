package com.salon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth settings from application.yml ({@code salon.auth.*}) (auth Phase 2).
 *
 * <p>The signing secret is never stored in source: application.yml reads it
 * from the {@code AMORE_TOKEN_SECRET} environment variable
 * ({@code ${AMORE_TOKEN_SECRET:}}). Generate it with a CSPRNG
 * ({@code openssl rand -hex 32}), keep it outside the repository, and set it
 * as a user-level environment variable before starting the app or running
 * the Spring test suite. Startup fails fast if it is missing or shorter
 * than 32 characters — there is no default and no fallback.
 *
 * @param tokenSecret     HMAC-SHA256 signing secret (>= 32 chars, from
 *                        environment variable {@code AMORE_TOKEN_SECRET})
 * @param tokenTtlMinutes bearer-token lifetime; fail-safe default 7 days
 */
@ConfigurationProperties(prefix = "salon.auth")
public record AuthProperties(String tokenSecret, long tokenTtlMinutes) {

	public AuthProperties {
		if (tokenSecret == null || tokenSecret.length() < 32) {
			throw new IllegalArgumentException(
					"salon.auth.token-secret (environment variable AMORE_TOKEN_SECRET) "
							+ "must be set to at least 32 characters.");
		}
		if (tokenTtlMinutes <= 0) {
			tokenTtlMinutes = 10080; // 7 days
		}
	}
}
