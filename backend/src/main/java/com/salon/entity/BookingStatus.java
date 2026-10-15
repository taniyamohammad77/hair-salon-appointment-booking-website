package com.salon.entity;

/**
 * Booking lifecycle. Stored as VARCHAR in the DB (CHECK constraint mirrors
 * these exact values). Only CONFIRMED bookings occupy a slot.
 */
public enum BookingStatus {
	CONFIRMED,
	COMPLETED,
	CANCELLED,
	NO_SHOW
}
