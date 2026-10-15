package com.salon.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Booking request — TRAP 4 by design: there is NO endTime, NO status and NO
 * id field here. Even a hand-crafted POST cannot inject them (Spring Boot
 * also ignores unknown JSON properties, so extra fields are dropped).
 * Everything else is re-validated in BookingService (never trust the client).
 */
@JsonIgnoreProperties(ignoreUnknown = true) // Trap 4: crafted extra fields are silently dropped
public record BookingRequest(
		@NotNull @Positive Long serviceId,
		@NotNull @Positive Long stylistId,
		@NotNull LocalDate date,
		@NotNull LocalTime time,
		@NotBlank @Size(max = 120) String customerName,
		@NotBlank @Size(max = 30) @Pattern(regexp = "[0-9+()\\- ]{6,}", message = "must be a valid phone number") String phone,
		@NotBlank @Size(max = 160) @Email String email,
		@Size(max = 500) String notes) {
}
