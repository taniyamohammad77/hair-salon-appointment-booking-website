package com.salon.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salon.dto.StylistDto;
import com.salon.entity.StylistEntity;
import com.salon.exception.ApiException;
import com.salon.repository.ServiceRepository;
import com.salon.repository.StylistRepository;
import com.salon.repository.StylistServiceRepository;

/**
 * Active stylists, optionally filtered to those offering a given service
 * (the service must exist — 404 otherwise, rule 10: never show false info).
 */
@Service
public class StylistCatalogService {

	private final StylistRepository stylistRepository;
	private final StylistServiceRepository stylistServiceRepository;
	private final ServiceRepository serviceRepository;

	public StylistCatalogService(StylistRepository stylistRepository,
			StylistServiceRepository stylistServiceRepository,
			ServiceRepository serviceRepository) {
		this.stylistRepository = stylistRepository;
		this.stylistServiceRepository = stylistServiceRepository;
		this.serviceRepository = serviceRepository;
	}

	@Transactional(readOnly = true)
	public List<StylistDto> listStylists(Long serviceId) {
		if (serviceId == null) {
			return stylistRepository.findByActiveTrueOrderByNameAsc().stream()
					.map(StylistDto::from)
					.toList();
		}

		serviceRepository.findByIdAndActiveTrue(serviceId)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that service."));

		List<Long> stylistIds = stylistServiceRepository.findByServiceId(serviceId).stream()
				.map(ss -> ss.getStylistId())
				.distinct()
				.toList();
		if (stylistIds.isEmpty()) {
			return List.of();
		}

		return stylistRepository.findByIdInAndActiveTrue(stylistIds).stream()
				.sorted(Comparator.comparing(StylistEntity::getName))
				.map(StylistDto::from)
				.toList();
	}
}
