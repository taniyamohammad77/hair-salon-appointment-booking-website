package com.salon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.StylistServiceEntity;
import com.salon.entity.StylistServiceId;

public interface StylistServiceRepository extends JpaRepository<StylistServiceEntity, StylistServiceId> {

	List<StylistServiceEntity> findByServiceId(Long serviceId);

	List<StylistServiceEntity> findByStylistId(Long stylistId);
}
