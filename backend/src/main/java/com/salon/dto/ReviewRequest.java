package com.salon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PHASE 4 — POST /api/reviews body. Rating is required 1–5 (mirrors
 * {@code CK_rev_rating}); the comment is optional but capped at 1000 chars
 * (mirrors the column). {@code bookingId} selects the reviewed booking —
 * the server re-checks ownership and completion eligibility, never trusting
 * the client.
 */
public record ReviewRequest(
		@NotNull(message = "Choose which booking to review.")
		Long bookingId,

		@NotNull(message = "Please choose a rating.")
		@Min(value = 1, message = "Rating must be between 1 and 5.")
		@Max(value = 5, message = "Rating must be between 1 and 5.")
		Integer rating,

		@Size(max = 1000, message = "Keep your review under 1000 characters.")
		String comment) {
}
