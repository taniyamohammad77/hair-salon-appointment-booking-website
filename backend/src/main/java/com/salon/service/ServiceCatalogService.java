package com.salon.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salon.dto.ServiceDto;
import com.salon.exception.ApiException;
import com.salon.repository.ServiceRepository;

/**
 * Read-only public catalogue (Phase 1 slice).
 */
@Service
public class ServiceCatalogService {

	private final ServiceRepository serviceRepository;

	public ServiceCatalogService(ServiceRepository serviceRepository) {
		this.serviceRepository = serviceRepository;
	}

	@Transactional(readOnly = true)
	public List<ServiceDto> listActiveServices() {
		return serviceRepository.findByActiveTrueOrderByNameAsc().stream()
				.map(ServiceDto::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ServiceDto getActiveService(Long id) {
		return serviceRepository.findByIdAndActiveTrue(id)
				.map(ServiceDto::from)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that service."));
	}
}
