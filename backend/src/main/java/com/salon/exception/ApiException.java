package com.salon.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain error — thrown by services, translated to clean JSON by
 * {@link GlobalExceptionHandler}. Never leaks stack traces to clients.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	/** Optional `Retry-After` (seconds) — set for 429 rate-limit responses so
	 *  clients know when the fixed window resets; null = no header. */
	private final Long retryAfterSeconds;

	public ApiException(HttpStatus status, String message) {
		this(status, message, null);
	}

	public ApiException(HttpStatus status, String message, Long retryAfterSeconds) {
		super(message);
		this.status = status;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public HttpStatus getStatus() {
		return status;
	}

	/** Seconds until the rate-limit window resets, or null when not a 429. */
	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, message);
	}

	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, message);
	}

	public static ApiException conflict(String message) {
		return new ApiException(HttpStatus.CONFLICT, message);
	}

	/** AUTH PHASE 2: bad credentials / missing-invalid-expired bearer token. */
	public static ApiException unauthorized(String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, message);
	}

	/** AUTH PHASE 3: authenticated, but not the owner of this resource. */
	public static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, message);
	}

	/** SECURITY: rate limit exceeded (fixed-window limiter, see RateLimitService). */
	public static ApiException tooManyRequests(String message) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
	}

	/** SECURITY: 429 that also carries a standard `Retry-After` header with
	 *  the seconds remaining in the current fixed window. */
	public static ApiException tooManyRequests(String message, long retryAfterSeconds) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, retryAfterSeconds);
	}
}
