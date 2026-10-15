package com.salon.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Holiday / leave for a stylist.
 * TRAP 2: start_time == null AND end_time == null means FULL-DAY off.
 * The availability engine must treat that as 00:00–24:00, never skip it.
 */
@Entity
@Table(name = "time_off")
public class TimeOffEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "stylist_id", nullable = false)
	private Long stylistId;

	@Column(name = "off_date", nullable = false)
	private LocalDate offDate;

	/** NULL start = off from midnight; NULL + NULL = full day off. */
	@Column(name = "start_time")
	private LocalTime startTime;

	/** NULL end = off until midnight; NULL + NULL = full day off. */
	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(name = "reason", length = 200)
	private String reason;

	public Long getId() {
		return id;
	}

	public Long getStylistId() {
		return stylistId;
	}

	public void setStylistId(Long stylistId) {
		this.stylistId = stylistId;
	}

	public LocalDate getOffDate() {
		return offDate;
	}

	public void setOffDate(LocalDate offDate) {
		this.offDate = offDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
