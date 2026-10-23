package com.salon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salon.entity.ReviewEntity;

/** PHASE 4 — access to dbo.reviews (one review per booking, DB-enforced). */
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

	/** Duplicate protection fast-path (the DB unique index is the final net). */
	boolean existsByBookingId(Long bookingId);

	/** Newest first; the service caps the page size. */
	List<ReviewEntity> findAllByOrderByCreatedAtDesc();
}
