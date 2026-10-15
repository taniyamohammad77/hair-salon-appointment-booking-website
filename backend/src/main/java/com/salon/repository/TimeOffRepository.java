package com.salon.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.TimeOffEntity;

public interface TimeOffRepository extends JpaRepository<TimeOffEntity, Long> {

	List<TimeOffEntity> findByStylistIdAndOffDate(Long stylistId, LocalDate offDate);
}
