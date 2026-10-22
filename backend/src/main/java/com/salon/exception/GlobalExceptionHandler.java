package com.salon.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One place that turns every failure into clean JSON (rule 8: no confusing
 * errors). Shapes:
 *   { "error": "human friendly message" }
 *   { "error": "...", "fields": { "name": "why it is invalid" } }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	public record ErrorResponse(String error, Map<String, String> fields) {
		public static ErrorResponse of(String message) {
			return new ErrorResponse(message, null);
		}
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
		// SECURITY: 429 rate-limit responses advertise when to retry (RFC 9110).
		// The value is seconds-to-reset only — no internals are ever exposed.
		Long retryAfter = ex.getRetryAfterSeconds();
		if (retryAfter != null) {
			builder.header("Retry-After", String.valueOf(retryAfter));
		}
		return builder.body(ErrorResponse.of(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("Please check the highlighted fields.", fields));
	}

	/** Validation failures on @RequestParam / @PathVariable constraints. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
		return ResponseEntity.badRequest().body(ErrorResponse.of("Please check the request parameters."));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of("Missing required parameter '" + ex.getParameterName() + "'."));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(ErrorResponse.of("The request body is malformed."));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of("'" + ex.getName() + "' has an invalid value."));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.of("That action isn't allowed here."));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("Not found."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of("Something went wrong on our side. Please try again."));
	}
}
