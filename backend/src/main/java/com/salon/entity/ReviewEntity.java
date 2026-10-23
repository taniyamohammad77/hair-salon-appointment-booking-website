package com.salon.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PHASE 4 — a guest's review of ONE booking (schema.sql §7:
 * {@code UQ_reviews_booking} = one review per booking, enforced by the DB;
 * {@code CK_rev_rating} = rating between 1 and 5). The table holds no guest
 * PII: names/service/stylist are resolved from the booking at read time.
 */
@Entity
@Table(name = "reviews")
public class ReviewEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "booking_id", nullable = false)
	private Long bookingId;

	/** TINYINT in the DB (matches CK_rev_rating 1–5). */
	@Column(name = "rating", nullable = false)
	private Byte rating;

	@Column(name = "comment", length = 1000)
	private String comment;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getBookingId() {
		return bookingId;
	}

	public void setBookingId(Long bookingId) {
		this.bookingId = bookingId;
	}

	public Byte getRating() {
		return rating;
	}

	public void setRating(Byte rating) {
		this.rating = rating;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
