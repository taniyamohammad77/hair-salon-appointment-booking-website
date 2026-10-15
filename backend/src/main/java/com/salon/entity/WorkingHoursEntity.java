package com.salon.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Weekly shift template: one row per stylist per weekday (1 = Monday ..
 * 7 = Sunday, ISO-8601 — matches java.time.DayOfWeek.getValue()).
 * DB enforces end > start and one row per (stylist, weekday).
 */
@Entity
@Table(name = "working_hours")
public class WorkingHoursEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "stylist_id", nullable = false)
	private Long stylistId;

	/** TINYINT in the DB (1 = Monday ... 7 = Sunday). */
	@Column(name = "weekday", nullable = false)
	private Byte weekday;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	public Long getId() {
		return id;
	}

	public Long getStylistId() {
		return stylistId;
	}

	public void setStylistId(Long stylistId) {
		this.stylistId = stylistId;
	}

	public Byte getWeekday() {
		return weekday;
	}

	public void setWeekday(Byte weekday) {
		this.weekday = weekday;
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
}
