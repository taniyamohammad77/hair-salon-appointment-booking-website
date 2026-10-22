package com.salon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salon.exception.ApiException;
import com.salon.service.RateLimitService;

/**
 * SECURITY unit tests for the trusted-proxy guard on client-IP resolution.
 * The limiter is only as strong as the key it counts against: if a client
 * could spoof {@code X-Forwarded-For} it could evade every per-IP limit, so
 * these tests pin down that the header is honored ONLY when the direct TCP
 * peer is a configured trusted proxy.
 */
class RateLimitInterceptorTest {

	private static final Set<String> NO_PROXIES = Set.of();
	private static final Set<String> TRUSTED = Set.of("10.0.0.1");

	@Test
	void untrustedPeerIgnoresForwardedForSpoof() {
		// client sends a forged XFF but connects directly → must key on the real peer
		assertEquals("203.0.113.5",
				RateLimitInterceptor.resolveClientIp("203.0.113.5", "1.2.3.4", NO_PROXIES));
	}

	@Test
	void emptyTrustConfigNeverHonorsHeader() {
		// default config (trusted-proxies: "") → header ignored even for loopback
		assertEquals("127.0.0.1",
				RateLimitInterceptor.resolveClientIp("127.0.0.1", "198.51.100.9", NO_PROXIES));
	}

	@Test
	void trustedProxyHonorsFirstForwardedHop() {
		assertEquals("198.51.100.9",
				RateLimitInterceptor.resolveClientIp("10.0.0.1", "198.51.100.9, 10.0.0.1", TRUSTED));
	}

	@Test
	void trustedProxyWithoutHeaderFallsBackToPeer() {
		assertEquals("10.0.0.1",
				RateLimitInterceptor.resolveClientIp("10.0.0.1", null, TRUSTED));
		assertEquals("10.0.0.1",
				RateLimitInterceptor.resolveClientIp("10.0.0.1", "   ", TRUSTED));
	}

	@Test
	void blankFirstHopFallsBackToPeer() {
		assertEquals("10.0.0.1",
				RateLimitInterceptor.resolveClientIp("10.0.0.1", " , 198.51.100.9", TRUSTED));
	}

	@Test
	void ipv4MappedAddressMatchesTrustedConfig() {
		Set<String> trusted = RateLimitInterceptor.parseTrustedProxies("127.0.0.1");
		assertEquals("198.51.100.9",
				RateLimitInterceptor.resolveClientIp("::FFFF:127.0.0.1", "198.51.100.9", trusted));
	}

	@Test
	void parseTrustedProxiesHandlesBlanksAndNormalization() {
		assertTrue(RateLimitInterceptor.parseTrustedProxies("").isEmpty());
		assertTrue(RateLimitInterceptor.parseTrustedProxies(null).isEmpty());
		assertTrue(RateLimitInterceptor.parseTrustedProxies("  ").isEmpty());

		Set<String> parsed = RateLimitInterceptor.parseTrustedProxies(" 127.0.0.1 , ::1 ,, ");
		assertEquals(2, parsed.size());
		assertTrue(parsed.contains("127.0.0.1"));
		assertTrue(parsed.contains("::1"));

		assertFalse(RateLimitInterceptor.parseTrustedProxies("10.0.0.1").contains("10.0.0.2"));
	}

	/** PHASE 2: end-to-end through preHandle — a client rotating FORGED
	 *  X-Forwarded-For headers on every attempt must still be limited by its
	 *  real peer address (default config: no trusted proxies). */
	@Test
	void forgedXffRotationThroughPreHandleStillHitsTheLimit() {
		AtomicLong clock = new AtomicLong(0); // frozen window — no test can flake on a minute rollover
		RateLimitService limiter = new RateLimitService(1, 1, 1, 1, clock::get);
		RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter, ""); // default: zero trusted proxies

		assertTrue(interceptor.preHandle(loginFrom("203.0.113.9", "1.2.3.4"),
				new MockHttpServletResponse(), null), "first attempt must pass");

		ApiException ex = assertThrows(ApiException.class,
				() -> interceptor.preHandle(loginFrom("203.0.113.9", "9.9.9.9"), // rotated forged header
						new MockHttpServletResponse(), null),
				"rotating XFF must NOT evade the per-IP limit");
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
	}

	/** PHASE 2: a TRUSTED proxy's forwarded client is honored (and limited per
	 *  forwarded client), proving configured proxies keep working end-to-end. */
	@Test
	void trustedProxyForwardedClientIsLimitedOnItsOwnKey() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(1, 1, 1, 1, clock::get);
		RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter, "10.0.0.1");

		// two different clients behind the SAME trusted proxy → independent keys
		assertTrue(interceptor.preHandle(loginFrom("10.0.0.1", "198.51.100.50"),
				new MockHttpServletResponse(), null));
		assertTrue(interceptor.preHandle(loginFrom("10.0.0.1", "198.51.100.51"),
				new MockHttpServletResponse(), null));
		// the FIRST forwarded client hits its own limit now
		assertThrows(ApiException.class, () -> interceptor.preHandle(
				loginFrom("10.0.0.1", "198.51.100.50"), new MockHttpServletResponse(), null));
	}

	private static MockHttpServletRequest loginFrom(String peer, String forgedXff) {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setMethod("POST");
		req.setRequestURI("/api/auth/login");
		req.setRemoteAddr(peer);
		req.addHeader("X-Forwarded-For", forgedXff);
		return req;
	}
}
