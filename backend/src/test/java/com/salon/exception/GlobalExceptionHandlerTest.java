package com.salon.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.salon.exception.GlobalExceptionHandler.ErrorResponse;

/**
 * PHASE 2 — shape of the HTTP 429 the client actually receives:
 * standard {@code Retry-After} header (seconds to window reset) plus the
 * existing generic JSON body — never any internal class/package/limit
 * details (rule: errors must not leak implementation information).
 */
class GlobalExceptionHandlerTest {

	private static final String GENERIC_429_MSG =
			"Too many attempts. Please wait a minute and try again.";

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void rateLimit429CarriesRetryAfterHeaderAndGenericBody() {
		ResponseEntity<ErrorResponse> res =
				handler.handleApiException(ApiException.tooManyRequests(GENERIC_429_MSG, 42));

		assertEquals(429, res.getStatusCode().value());
		assertEquals("42", res.getHeaders().getFirst("Retry-After"));

		assertNotNull(res.getBody());
		assertEquals(GENERIC_429_MSG, res.getBody().error());
		assertNull(res.getBody().fields(), "429 must not carry a fields map");

		// SECURITY: nothing internal may appear in the payload
		assertFalse(res.getBody().error().contains("com.salon"));
		assertFalse(res.getBody().error().contains("RateLimit"));
		assertFalse(res.getBody().error().contains("Exception"));
		assertTrue(res.getHeaders().getAccessControlAllowOrigin() == null
				|| !res.getHeaders().getAccessControlAllowOrigin().contains("*"),
				"CORS must not be widened by error responses");
	}

	@Test
	void non429ApiErrorsCarryNoRetryAfterHeader() {
		ResponseEntity<ErrorResponse> res =
				handler.handleApiException(ApiException.notFound("Not found."));
		assertEquals(404, res.getStatusCode().value());
		assertNull(res.getHeaders().getFirst("Retry-After"));
	}

	@Test
	void tooManyRequestsWithoutSecondsOmitsHeaderInsteadOfFailing() {
		ResponseEntity<ErrorResponse> res =
				handler.handleApiException(ApiException.tooManyRequests(GENERIC_429_MSG));
		assertEquals(429, res.getStatusCode().value());
		assertNull(res.getHeaders().getFirst("Retry-After"));
		assertEquals(GENERIC_429_MSG, res.getBody().error());
	}
}
