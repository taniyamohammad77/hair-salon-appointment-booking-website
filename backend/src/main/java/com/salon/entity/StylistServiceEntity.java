package com.salon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Junction table for the stylist ↔ service M:N relationship.
 * Plain FK columns (no associations) — lookups are simple and fast.
 */
@Entity
@Table(name = "stylists_services")
@IdClass(StylistServiceId.class)
public class StylistServiceEntity {

	@Id
	@Column(name = "stylist_id", nullable = false)
	private Long stylistId;

	@Id
	@Column(name = "service_id", nullable = false)
	private Long serviceId;

	public StylistServiceEntity() {
	}

	public StylistServiceEntity(Long stylistId, Long serviceId) {
		this.stylistId = stylistId;
		this.serviceId = serviceId;
	}

	public Long getStylistId() {
		return stylistId;
	}

	public Long getServiceId() {
		return serviceId;
	}
}
