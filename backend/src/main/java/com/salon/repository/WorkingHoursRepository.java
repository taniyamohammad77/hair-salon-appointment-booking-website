package com.salon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.WorkingHoursEntity;

public interface WorkingHoursRepository extends JpaRepository<WorkingHoursEntity, Long> {

	List<WorkingHoursEntity> findByStylistIdAndWeekday(Long stylistId, Byte weekday);

	List<WorkingHoursEntity> findByStylistId(Long stylistId);
}
