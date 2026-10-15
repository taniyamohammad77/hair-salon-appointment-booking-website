package com.salon.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salon.dto.AuthDtos.AuthResponse;
import com.salon.dto.AuthDtos.LoginRequest;
import com.salon.dto.AuthDtos.SignupRequest;
import com.salon.dto.AuthDtos.UserResponse;
import com.salon.service.AuthService;

import jakarta.validation.Valid;

/**
 * Customer authentication endpoints (auth Phase 2). Errors come back in the
 * project's standard JSON shape via GlobalExceptionHandler — 409 duplicate
 * email, 400 validation, 401 bad credentials / missing-invalid-expired
 * token. No Spring Security: /api/auth/me reads the Authorization header
 * itself and delegates verification to TokenService.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
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
}
