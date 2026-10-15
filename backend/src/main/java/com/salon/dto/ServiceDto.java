package com.salon.dto;

import java.math.BigDecimal;

import com.salon.entity.ServiceEntity;

/**
 * Public JSON shape of a service. Prices and times come from the DB only
 * (rule 10) — the client never supplies them.
 */
public record ServiceDto(
		Long id,
		String name,
		String description,
		String category,
		int durationMinutes,
		int bufferMinutes,
		BigDecimal price) {

	public static ServiceDto from(ServiceEntity e) {
		return new ServiceDto(e.getId(), e.getName(), e.getDescription(), e.getCategory(),
				e.getDurationMinutes(), e.getBufferMinutes(), e.getPrice());
	}
}
