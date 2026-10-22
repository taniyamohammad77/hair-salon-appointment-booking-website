package com.salon.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.salon.exception.ApiException;

/**
 * SECURITY: per-client-IP fixed-window limiter for sensitive WRITE endpoints
 * (login / signup / booking mutations). In-memory, thread-safe, zero new
 * dependencies — right-sized for the local demo. A real deployment should
 * move the window map to a shared store (e.g. Redis).
 */
@Service
public class RateLimitService {

	private final int loginPerMinute;
	private final int signupPerMinute;
	private final int bookingPerMinute;
	private final int mutationPerMinute;
	private final LongSupplier clock; // injectable so tests don't sleep 60s

	private record Window(long epochMinute, int count) {}

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	/** SECURITY: last minute a stale-window sweep ran — cleanup no longer
	 *  depends on some client crossing a limit (see check()). */
	private final AtomicLong lastSweepMinute = new AtomicLong(Long.MIN_VALUE);

	/** @Autowired: two constructors exist, so Spring must be told which one
	 *  creates the bean (the other is the package-private test hook below). */
	@Autowired
	public RateLimitService(
			@Value("${salon.rate-limit.login-per-minute:30}") int loginPerMinute,
			@Value("${salon.rate-limit.signup-per-minute:10}") int signupPerMinute,
			@Value("${salon.rate-limit.booking-per-minute:30}") int bookingPerMinute,
			@Value("${salon.rate-limit.mutation-per-minute:30}") int mutationPerMinute) {
		this(loginPerMinute, signupPerMinute, bookingPerMinute, mutationPerMinute,
				System::currentTimeMillis);
	}

	/** Test hook (used by tests in BOTH com.salon.service and com.salon.config):
	 *  injectable clock so window-rollover & forged-header tests never sleep. */
	public RateLimitService(int loginPerMinute, int signupPerMinute, int bookingPerMinute,
			int mutationPerMinute, LongSupplier clock) {
		this.loginPerMinute = loginPerMinute;
		this.signupPerMinute = signupPerMinute;
		this.bookingPerMinute = bookingPerMinute;
		this.mutationPerMinute = mutationPerMinute;
		this.clock = clock;
	}

	public void checkLogin(String ip) {
		check("login", ip, loginPerMinute);
	}

	public void checkSignup(String ip) {
		check("signup", ip, signupPerMinute);
	}

	public void checkBooking(String ip) {
		check("booking-write", ip, bookingPerMinute);
	}

	public void checkMutation(String ip) {
		check("mutation", ip, mutationPerMinute);
	}

	void check(String bucket, String ip, int limitPerMinute) {
		String key = bucket + "|" + ip;
		long now = clock.getAsLong();
		long minute = now / 60_000L;
		// SECURITY: bounded memory — sweep windows from 5+ minutes ago at most
		// once per minute, whichever thread gets there first. Without this the
		// map only shrank inside the over-limit branch, so many distinct
		// under-limit clients could grow it indefinitely.
		long lastSweep = lastSweepMinute.get();
		if (lastSweep != minute && lastSweepMinute.compareAndSet(lastSweep, minute)) {
			windows.entrySet().removeIf(e -> e.getValue().epochMinute() < minute - 5);
		}
		int used = windows.compute(key,
				(k, w) -> (w == null || w.epochMinute() != minute)
						? new Window(minute, 1)
						: new Window(minute, w.count() + 1))
				.count();
		if (used > limitPerMinute) {
			// SECURITY: tell honest clients when to come back (seconds left in
			// this fixed window) — generic message, no internals.
			long retryAfterSeconds = Math.max(1L, 60L - (now % 60_000L) / 1000L);
			throw ApiException.tooManyRequests(
					"Too many attempts. Please wait a minute and try again.",
					retryAfterSeconds);
		}
	}

	/** Test hook: how many windows are currently tracked (sweep assertions). */
	int trackedWindows() {
		return windows.size();
	}
}
