package com.salon.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.salon.exception.ApiException;

/**
 * SECURITY unit tests for the fixed-window rate limiter. A fake clock drives
 * window rollover, so no test sleeps. Integration suites run with raised
 * limits (src/test/resources/application.properties) so feature tests stay
 * independent of the limiter; live 429 behavior is probed with curl after
 * startup (see PROJECT-NOTES rate-limit work log).
 */
class RateLimitServiceTest {

	private static final String IP = "203.0.113.7";

	@Test
	void allowsUpToTheLimitThenBlocks429() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(2, 2, 2, 2, 2, clock::get);

		assertDoesNotThrow(() -> limiter.checkLogin(IP));
		assertDoesNotThrow(() -> limiter.checkLogin(IP));

		ApiException ex = assertThrows(ApiException.class, () -> limiter.checkLogin(IP));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
	}

	@Test
	void windowResetsInTheNextMinute() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(1, 1, 1, 1, 1, clock::get);

		assertDoesNotThrow(() -> limiter.checkLogin(IP));
		assertThrows(ApiException.class, () -> limiter.checkLogin(IP));

		clock.set(60_000L); // next fixed window
		assertDoesNotThrow(() -> limiter.checkLogin(IP));
	}

	@Test
	void bucketsAreIndependentPerIpAndEndpoint() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(1, 1, 1, 1, 1, clock::get);

		assertDoesNotThrow(() -> limiter.checkLogin(IP));
		assertDoesNotThrow(() -> limiter.checkLogin("198.51.100.9")); // other IP unaffected
		assertDoesNotThrow(() -> limiter.checkSignup(IP)); // other bucket unaffected
		assertThrows(ApiException.class, () -> limiter.checkLogin(IP));
	}

	/** PHASE 2: exact boundary — request #limit passes, #limit+1 = 429. */
	@Test
	void boundaryExactlyLimitPassesAndOneMoreIs429() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(3, 3, 3, 3, 3, clock::get);

		for (int i = 1; i <= 3; i++) {
			assertDoesNotThrow(() -> limiter.checkLogin(IP), "request #" + i + " must pass");
		}
		ApiException ex = assertThrows(ApiException.class, () -> limiter.checkLogin(IP));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
	}

	/** PHASE 2: N threads x M attempts against one bucket must admit EXACTLY
	 *  the limit — no lost increments, no double admits (ConcurrentHashMap
	 *  compute is atomic per key). */
	@Test
	void concurrentRequestsAreCountedWithoutRace() throws Exception {
		final int limit = 50, threads = 8, perThread = 25; // 200 attempts total
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(limit, limit, limit, limit, limit, clock::get);

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		AtomicInteger admitted = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		try {
			for (int t = 0; t < threads; t++) {
				pool.submit(() -> {
					try {
						startGate.await();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
					for (int i = 0; i < perThread; i++) {
						try {
							limiter.checkLogin(IP);
							admitted.incrementAndGet();
						} catch (ApiException ex) {
							rejected.incrementAndGet();
						}
					}
				});
			}
			startGate.countDown();
			pool.shutdown();
			assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "worker pool must finish");
		} finally {
			pool.shutdownNow();
		}
		assertEquals(limit, admitted.get(), "exactly the limit must be admitted");
		assertEquals(threads * perThread - limit, rejected.get(), "every attempt over the limit must be a 429");
	}

	/** PHASE 2: the 429 message must be generic (no class/package/limit
	 *  internals) and the exception must carry Retry-After seconds in [1,60]. */
	@Test
	void fortyTwoNineIsGenericAndCarriesRetryAfterSeconds() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(1, 1, 1, 1, 1, clock::get);

		limiter.checkLogin(IP);
		ApiException ex = assertThrows(ApiException.class, () -> limiter.checkLogin(IP));

		assertEquals("Too many attempts. Please wait a minute and try again.", ex.getMessage());
		assertFalse(ex.getMessage().contains("com.salon"));
		assertFalse(ex.getMessage().contains("RateLimit"));
		assertFalse(ex.getMessage().contains("Exception"));

		Long retryAfter = ex.getRetryAfterSeconds();
		assertNotNull(retryAfter, "429 must carry Retry-After seconds");
		assertTrue(retryAfter >= 1 && retryAfter <= 60,
				"Retry-After must be seconds remaining in the window, was " + retryAfter);
	}

	/** PHASE 2: stale windows must be swept by the periodic minute sweep even
	 *  when NO client ever crosses a limit (map cannot bloat unbounded). */
	@Test
	void staleWindowsAreSweptWithoutAnyLimitCrossing() {
		AtomicLong clock = new AtomicLong(0);
		RateLimitService limiter = new RateLimitService(10, 10, 10, 10, 10, clock::get);

		// three DIFFERENT windows at minute 0, all far below their limits
		limiter.checkLogin("203.0.113.1");
		limiter.checkLogin("203.0.113.2");
		limiter.checkSignup("203.0.113.3");
		assertEquals(3, limiter.trackedWindows());

		clock.set(10 * 60_000L); // minute 10 — still nobody over any limit
		limiter.checkLogin("203.0.113.4"); // triggers the periodic sweep, then records minute 10

		assertEquals(1, limiter.trackedWindows(),
				"windows older than 5 minutes must be swept without a limit-cross event");
		assertDoesNotThrow(() -> limiter.checkLogin("203.0.113.4")); // new window still works
	}
}
