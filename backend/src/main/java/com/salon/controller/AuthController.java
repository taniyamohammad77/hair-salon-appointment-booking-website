package com.salon.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salon.dto.AuthDtos.AuthResponse;
import com.salon.dto.AuthDtos.LoginRequest;
import com.salon.dto.AuthDtos.MessageResponse;
import com.salon.dto.AuthDtos.ResendVerificationRequest;
import com.salon.dto.AuthDtos.SignupRequest;
import com.salon.dto.AuthDtos.UserResponse;
import com.salon.dto.AuthDtos.VerificationResponse;
import com.salon.service.AuthService;
import com.salon.service.VerificationService;

import jakarta.validation.Valid;

/**
 * Customer authentication endpoints (auth Phase 2). Errors come back in the
 * project's standard JSON shape via GlobalExceptionHandler — 409 duplicate
 * email, 400 validation, 401 bad credentials / missing-invalid-expired
 * token. No Spring Security: /api/auth/me reads the Authorization header
 * itself and delegates verification to TokenService.
 *
 * PHASE 5: email verification — GET /api/auth/verify (public, token in the
 * query string) and POST /api/auth/resend-verification (public, rate-limited
 * to 5/min/IP by the interceptor). Neither requires a session: verification
 * happens from the emailed link, resend from before/after login.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final VerificationService verificationService;

	public AuthController(AuthService authService, VerificationService verificationService) {
		this.authService = authService;
		this.verificationService = verificationService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest request) {
		return authService.signup(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/me")
	public UserResponse me(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return authService.currentUser(authorization);
	}

	/** PHASE 5: consume an emailed verification token. Public by design —
	 *  the token IS the credential. Invalid/used/expired → 400 in the
	 *  standard {error} shape; success → {verified:true, message}. */
	@GetMapping("/verify")
	public VerificationResponse verify(@RequestParam("token") String token) {
		return verificationService.verify(token);
	}

	/** PHASE 5: request a fresh verification link. Rate-limited (5/min/IP).
	 *  Response is always the same generic message — no account enumeration. */
	@PostMapping("/resend-verification")
	public MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		return verificationService.resend(request.email());
	}
}
