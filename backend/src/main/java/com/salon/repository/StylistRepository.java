package com.salon.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.StylistEntity;

public interface StylistRepository extends JpaRepository<StylistEntity, Long> {

	List<StylistEntity> findByActiveTrueOrderByNameAsc();

	/** Batch load by ids, active only (order preserved by the caller). */
	List<StylistEntity> findByIdInAndActiveTrue(Collection<Long> ids);

	Optional<StylistEntity> findByIdAndActiveTrue(Long id);
}
