package com.salon.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.salon.service.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SECURITY: routes sensitive WRITE endpoints through {@link RateLimitService}.
 * Registered in {@link WebConfig} for /api/auth/login, /api/auth/signup and
 * /api/bookings/**; the method checks below keep GET /api/bookings (My Visits)
 * and public catalogue/availability reads unlimited on purpose.
 *
 * <p>SECURITY — client IP resolution: {@code X-Forwarded-For} is attacker
 * controlled, so it is honored ONLY when the direct TCP peer
 * ({@code request.getRemoteAddr()}) is listed in
 * {@code salon.rate-limit.trusted-proxies}. With no proxies configured (the
 * local-demo default) the header is ignored entirely and the limiter keys on
 * the real peer address — a client cannot spoof its way past the limits by
 * rotating forged XFF headers.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final RateLimitService rateLimiter;

	/** Normalized trusted-proxy addresses (exact match after {@link #normalize}). */
	private final Set<String> trustedProxies;

	public RateLimitInterceptor(RateLimitService rateLimiter,
			@Value("${salon.rate-limit.trusted-proxies:}") String trustedProxies) {
		this.rateLimiter = rateLimiter;
		this.trustedProxies = parseTrustedProxies(trustedProxies);
	}

	/** Parses the comma-separated config value into normalized addresses.
	 *  Package-private + static so unit tests cover the parsing directly. */
	static Set<String> parseTrustedProxies(String configured) {
		if (configured == null || configured.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(configured.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(RateLimitInterceptor::normalize)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		String ip = clientIp(request);

		if ("/api/auth/login".equals(path)) {
			rateLimiter.checkLogin(ip);
		}
		else if ("/api/auth/signup".equals(path)) {
			rateLimiter.checkSignup(ip);
		}
		else if ("POST".equals(method)) {
			rateLimiter.checkBooking(ip); // POST /api/bookings
		}
		else if ("PUT".equals(method)) {
			rateLimiter.checkMutation(ip); // cancel / reschedule
		}
		// GET /api/bookings (My Visits) intentionally unlimited
		return true;
	}

	private String clientIp(HttpServletRequest request) {
		return resolveClientIp(request.getRemoteAddr(),
				request.getHeader("X-Forwarded-For"), trustedProxies);
	}

	/**
	 * SECURITY: returns the peer address unless the peer is a trusted proxy
	 * AND supplied a usable X-Forwarded-For value; then the first (client) hop.
	 * Package-private + static so unit tests exercise the trust logic directly.
	 */
	static String resolveClientIp(String remoteAddr, String forwardedFor, Set<String> trustedProxies) {
		String remote = normalize(remoteAddr == null ? "" : remoteAddr);
		if (!trustedProxies.contains(remote) || forwardedFor == null || forwardedFor.isBlank()) {
			return remote; // untrusted peer → header is spoofed, ignore it
		}
		String firstHop = forwardedFor.split(",")[0];
		String client = normalize(firstHop);
		return client.isEmpty() ? remote : client;
	}

	/** Trim + lowercase IPv6, strip IPv4-mapped {@code ::ffff:} prefix so
	 *  {@code ::ffff:127.0.0.1} and {@code 127.0.0.1} compare equal. */
	static String normalize(String addr) {
		String a = addr.trim();
		if (a.regionMatches(true, 0, "::ffff:", 0, 7)) {
			a = a.substring(7);
		}
		return a.toLowerCase();
	}
}
