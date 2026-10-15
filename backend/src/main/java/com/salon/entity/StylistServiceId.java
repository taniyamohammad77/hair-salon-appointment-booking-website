package com.salon.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite PK of the stylists_services junction table — its existence
 * alone makes duplicate (stylist, service) pairs impossible.
 */
public class StylistServiceId implements Serializable {

	private Long stylistId;
	private Long serviceId;

	public StylistServiceId() {
	}

	public StylistServiceId(Long stylistId, Long serviceId) {
		this.stylistId = stylistId;
		this.serviceId = serviceId;
	}

	public Long getStylistId() {
		return stylistId;
	}

	public Long getServiceId() {
		return serviceId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StylistServiceId other)) {
			return false;
		}
		return Objects.equals(stylistId, other.stylistId) && Objects.equals(serviceId, other.serviceId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(stylistId, serviceId);
	}
}
