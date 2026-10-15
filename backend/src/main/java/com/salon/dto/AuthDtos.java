package com.salon.dto;

import com.salon.entity.UserEntity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Auth request/response DTOs (auth Phase 2).
 *
 * SECURITY: there is deliberately NO response record containing the
 * password or its hash — signup/login/me answers carry identity only
 * (id, name, email) plus the bearer token on login.
 *
 * Validation mirrors the frontend's auth.js rules (name 2–120, email ≤160)
 * plus the approved password policy (minimum 8, maximum 72 — BCrypt only
 * hashes the first 72 bytes, so anything longer is silently truncated and
 * must be rejected up front).
 */
public final class AuthDtos {

	private AuthDtos() {
	}

	/** POST /api/auth/signup */
	public record SignupRequest(
			@NotBlank(message = "Name is required.")
			@Size(min = 2, max = 120, message = "Name must be 2–120 characters.")
			String name,

			@NotBlank(message = "Email is required.")
			@Size(max = 160, message = "Email must be 160 characters or fewer.")
			@Email(message = "Enter a valid email address.")
			String email,

			@NotBlank(message = "Password is required.")
			@Size(min = 8, max = 72, message = "Password must be 8–72 characters.")
			String password) {
	}

	/** POST /api/auth/login — password has NO minimum here: a wrong
	 *  (even short) password must produce 401, not a 400 validation error. */
	public record LoginRequest(
			@NotBlank(message = "Email is required.")
			@Size(max = 160, message = "Email must be 160 characters or fewer.")
			@Email(message = "Enter a valid email address.")
			String email,

			@NotBlank(message = "Password is required.")
			String password) {
	}

	/** Identity payload for signup + /me — never includes any secret.
	 *  PHASE 5A: verified=false means legacy (pre-account) bookings stay
	 *  hidden until the salon verifies the email address. */
	public record UserResponse(Long id, String name, String email, boolean verified) {

		public static UserResponse from(UserEntity user) {
			return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.isEmailVerified());
		}
	}

	/** POST /api/auth/login — bearer token + the authenticated identity
	 *  (PHASE 5A: verified rides along so the UI can hint about legacy rows). */
	public record AuthResponse(String token, Long id, String name, String email, boolean verified) {
	}
}
