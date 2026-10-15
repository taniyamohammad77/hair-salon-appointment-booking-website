package com.salon.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.ServiceEntity;
import com.salon.entity.StylistEntity;

/**
 * Public JSON shape of a booking. The id doubles as the booking reference
 * shown to the customer.
 */
public record BookingResponse(
		Long id,
		Long serviceId,
		String serviceName,
		Long stylistId,
		String stylistName,
		String customerName,
		String email,
		LocalDate date,
		@JsonFormat(pattern = "HH:mm") LocalTime time,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		BookingStatus status,
		String notes) {

	public static BookingResponse from(BookingEntity b, ServiceEntity s, StylistEntity st) {
		return new BookingResponse(
				b.getId(),
				b.getServiceId(),
				s != null ? s.getName() : null,
				b.getStylistId(),
				st != null ? st.getName() : null,
				b.getCustomerName(),
				b.getEmail(),
				b.getBookingDate(),
				b.getBookingTime(),
				b.getEndTime(),
				b.getStatus(),
				b.getNotes());
	}
}
