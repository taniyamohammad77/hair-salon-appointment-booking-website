package com.salon.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A bookable salon service. Soft delete only: active = false keeps every
 * old booking's history intact (rule: no broken FKs, no false data).
 */
@Entity
@Table(name = "services")
public class ServiceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", length = 1000)
	private String description;

	@Column(name = "category", length = 60)
	private String category;

	/** How long the chair is occupied, in minutes (> 0, DB CHECK enforced). */
	@Column(name = "duration_minutes", nullable = false)
	private Integer durationMinutes;

	/** Cleanup / changeover time appended after the service (>= 0). */
	@Column(name = "buffer_minutes", nullable = false)
	private Integer bufferMinutes;

	@Column(name = "price", nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	@Column(name = "active", nullable = false)
	private Boolean active;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Integer getBufferMinutes() {
		return bufferMinutes;
	}

	public void setBufferMinutes(Integer bufferMinutes) {
		this.bufferMinutes = bufferMinutes;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}
}
