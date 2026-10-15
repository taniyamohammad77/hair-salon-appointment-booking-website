package com.salon.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An appointment. All four ingredients are NOT NULL: service, stylist,
 * date, time (rule 1).
 * TRAP 4: {@code endTime} is computed by the backend only —
 * booking_time + service.duration + service.buffer. It is never accepted
 * from the client (the request DTO has no such field at all).
 */
@Entity
@Table(name = "bookings")
public class BookingEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_name", nullable = false, length = 120)
	private String customerName;

	@Column(name = "phone", nullable = false, length = 30)
	private String phone;

	@Column(name = "email", nullable = false, length = 160)
	private String email;

	@Column(name = "service_id", nullable = false)
	private Long serviceId;

	@Column(name = "stylist_id", nullable = false)
	private Long stylistId;

	@Column(name = "booking_date", nullable = false)
	private LocalDate bookingDate;

	@Column(name = "booking_time", nullable = false)
	private LocalTime bookingTime;

	/** Server-computed: bookingTime + duration + buffer. */
	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "status", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private BookingStatus status;

	@Column(name = "notes", length = 500)
	private String notes;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public Long getId() {
		return id;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Long getServiceId() {
		return serviceId;
	}

	public void setServiceId(Long serviceId) {
		this.serviceId = serviceId;
	}

	public Long getStylistId() {
		return stylistId;
	}

	public void setStylistId(Long stylistId) {
		this.stylistId = stylistId;
	}

	public LocalDate getBookingDate() {
		return bookingDate;
	}

	public void setBookingDate(LocalDate bookingDate) {
		this.bookingDate = bookingDate;
	}

	public LocalTime getBookingTime() {
		return bookingTime;
	}

	public void setBookingTime(LocalTime bookingTime) {
		this.bookingTime = bookingTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public void setStatus(BookingStatus status) {
		this.status = status;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
