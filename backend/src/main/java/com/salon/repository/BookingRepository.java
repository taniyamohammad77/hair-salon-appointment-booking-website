package com.salon.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

	/** Busy intervals for the availability engine (Trap 1). */
	List<BookingEntity> findByStylistIdAndBookingDateAndStatusOrderByBookingTimeAsc(
			Long stylistId, LocalDate bookingDate, BookingStatus status);

	/** "My appointments" lookup by email (newest first). */
	List<BookingEntity> findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(String email);

	Optional<BookingEntity> findByIdAndStatus(Long id, BookingStatus status);

	/**
	 * TRAP 3 (race condition): the critical section of the save flow first
	 * takes a pessimistic write lock over the stylist's confirmed bookings
	 * for that date (SQL Server: UPDLOCK — held until commit). A concurrent
	 * booking for the same slot blocks here until the first transaction
	 * commits, then re-evaluates with fresh data and gets HTTP 409.
	 * The filtered unique index UQ_bookings_slot remains the final backstop.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select b from BookingEntity b
			where b.stylistId = :stylistId
			  and b.bookingDate = :date
			  and b.status = :status
			""")
	List<BookingEntity> lockConfirmedBookingsForDate(@Param("stylistId") Long stylistId,
			@Param("date") LocalDate date, @Param("status") BookingStatus status);
}
