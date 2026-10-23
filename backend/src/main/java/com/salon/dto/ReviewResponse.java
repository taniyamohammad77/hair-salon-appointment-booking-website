package com.salon.dto;

/**
 * PHASE 4 — a published review. Shape matches the frontend's long-standing
 * SAMPLE_REVIEWS contract ({@code id / rating / comment / guestName /
 * serviceName}, {@code createdAt} as an ISO date) plus {@code stylistName}.
 * {@code guestName} is the booking's customer name MASKED to first name +
 * surname initial by the server — reviews are public, so the full name is
 * never published.
 */
public record ReviewResponse(
		Long id,
		Long bookingId,
		int rating,
		String comment,
		String guestName,
		String serviceName,
		String stylistName,
		String createdAt) {
}
