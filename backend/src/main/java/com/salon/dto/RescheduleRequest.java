package com.salon.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

/**
 * Reschedule request: new date + start time only. The service/stylist stay
 * unchanged; the new end time is computed server-side (Trap 4).
 */
public record RescheduleRequest(
		@NotNull LocalDate date,
		@NotNull LocalTime time) {
}
