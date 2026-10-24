package com.salon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.salon.exception.ApiException;
import com.salon.service.RateLimitService;

/**
 * PHASE 2 — endpoint scoping of the rate limiter, verified through the REAL
 * interceptor's preHandle (the exact code path Spring MVC invokes).
 *
 * The limiter is built with ALL limits = 0, so any request the interceptor
 * decides to check must be rejected with 429 — which makes these tests a
 * precise probe of scoping: throttled routes fail, unthrottled routes pass,
 * regardless of wall-clock timing.
 */
class RateLimitEndpointScopingTest {

	/** Every checked bucket rejects immediately (limit 0 → first use = 429). */
	private final RateLimitService zeroedLimiter = new RateLimitService(0, 0, 0, 0, 0);

	private final RateLimitInterceptor interceptor =
			new RateLimitInterceptor(zeroedLimiter, ""); // default: no trusted proxies

	private static MockHttpServletRequest request(String method, String uri) {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setMethod(method);
		req.setRequestURI(uri);
		req.setRemoteAddr("198.51.100.7");
		return req;
	}

	@Test
	void loginSignupAndResendAreThrottled() {
		for (String path : new String[] { "/api/auth/login", "/api/auth/signup",
				"/api/auth/resend-verification" }) {
			ApiException ex = assertThrows(ApiException.class,
					() -> interceptor.preHandle(request("POST", path),
							new MockHttpServletResponse(), null),
					path + " must be rate-limited");
			assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus(), path);
		}
	}

	/** PHASE 5: verify is a public READ (token = credential) and must stay
	 *  unlimited — only the resend WRITE is throttled. */
	@Test
	void verificationReadIsNeverThrottled() {
		assertTrue(interceptor.preHandle(request("GET", "/api/auth/verify"),
				new MockHttpServletResponse(), null),
				"GET /api/auth/verify must stay unlimited");
	}

	@Test
	void bookingWritesAreThrottledButBookingReadsAreNot() {
		// writes → checked (429 with limit 0)
		for (String[] write : new String[][] {
				{ "POST", "/api/bookings" },
				{ "PUT", "/api/bookings/40075/cancel" },
				{ "PUT", "/api/bookings/40075/reschedule" } }) {
			ApiException ex = assertThrows(ApiException.class,
					() -> interceptor.preHandle(request(write[0], write[1]),
							new MockHttpServletResponse(), null),
					write[0] + " " + write[1] + " must be rate-limited");
			assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
		}
		// My Visits read → intentionally unlimited
		assertTrue(interceptor.preHandle(request("GET", "/api/bookings"),
				new MockHttpServletResponse(), null),
				"GET /api/bookings (My Visits) must stay unlimited");
	}

	@Test
	void publicCatalogueAndHealthReadsAreNeverChecked() {
		for (String path : new String[] {
				"/api/health", "/api/services", "/api/stylists", "/api/availability" }) {
			assertTrue(interceptor.preHandle(request("GET", path),
					new MockHttpServletResponse(), null),
					"public GET " + path + " must never be rate-limited");
		}
	}

	/** Pins WebConfig's registration so a future edit cannot silently widen
	 *  or drop the limited paths. */
	@Test
	void webConfigRegistersExactlyTheSensitivePaths() {
		InterceptorRegistry registry = mock(InterceptorRegistry.class);
		InterceptorRegistration registration = mock(InterceptorRegistration.class);
		when(registry.addInterceptor(any())).thenReturn(registration);

		RateLimitInterceptor bean = new RateLimitInterceptor(
				new RateLimitService(1, 1, 1, 1, 1), "");
		new WebConfig(bean).addInterceptors(registry);

		verify(registry).addInterceptor(bean);
		verify(registration).addPathPatterns(
				"/api/auth/login", "/api/auth/signup",
				"/api/auth/resend-verification", "/api/bookings/**");
	}
}
