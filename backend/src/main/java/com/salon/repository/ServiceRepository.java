package com.salon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.ServiceEntity;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

	/** Public catalogue: only active services, alphabetically. */
	List<ServiceEntity> findByActiveTrueOrderByNameAsc();

	Optional<ServiceEntity> findByIdAndActiveTrue(Long id);
}
