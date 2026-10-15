package com.salon.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salon.dto.BookingRequest;
import com.salon.dto.BookingResponse;
import com.salon.dto.RescheduleRequest;
import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.ServiceEntity;
import com.salon.entity.StylistEntity;
import com.salon.entity.UserEntity;
import com.salon.exception.ApiException;
import com.salon.repository.BookingRepository;
import com.salon.repository.ServiceRepository;
import com.salon.repository.StylistRepository;

/**
 * Booking use-cases. Data integrity is non-negotiable (rule 7):
 *
 *  - Trap 4: end_time is computed HERE from the DB service row — the request
 *    DTO has no endTime field, so it can never be injected.
 *  - Trap 1: the requested interval is re-checked against real availability
 *    inside the save transaction (same engine that feeds the UI).
 *  - Trap 3: re-check + INSERT happen inside one transaction that first takes
 *    a pessimistic lock on the stylist's bookings for that date; the DB's
 *    filtered unique index is the last line of defense.
 */
@Service
public class BookingService {

	/**
	 * PHASE 5A: the authenticated caller, carrying what the ownership gate
	 * needs. {@code verified} comes from users.email_verified — an unverified
	 * account must not inherit bookings that existed before the account did
	 * (signup-email matching alone proves nothing about legacy rows).
	 */
	public record BookingOwner(String email, boolean verified, java.time.LocalDateTime accountCreatedAt) {

		public static BookingOwner from(UserEntity user) {
			return new BookingOwner(user.getEmail(), user.isEmailVerified(), user.getCreatedAt());
		}
	}

	private final BookingRepository bookingRepository;
	private final AvailabilityService availabilityService;
	private final ServiceRepository serviceRepository;
	private final StylistRepository stylistRepository;

	public BookingService(BookingRepository bookingRepository,
			AvailabilityService availabilityService,
			ServiceRepository serviceRepository,
			StylistRepository stylistRepository) {
		this.bookingRepository = bookingRepository;
		this.availabilityService = availabilityService;
		this.serviceRepository = serviceRepository;
		this.stylistRepository = stylistRepository;
	}

	@Transactional
	public BookingResponse createBooking(BookingRequest req, BookingOwner owner) {
		// AUTH PHASE 3: the account is the booking's identity. The request
		// email must BE the account email — booking for another address is a
		// 403, and the stored email is always the token's (never client-set).
		// (PHASE 5A: creating bookings needs no verification — the gate only
		// protects access to bookings that pre-date the account.)
		if (!req.email().trim().equalsIgnoreCase(owner.email())) {
			throw ApiException.forbidden("Bookings must use your own account email (" + owner.email() + ").");
		}
		// Server-side clock — never trust a client-supplied "today".
		LocalDate today = LocalDate.now();
		if (req.date().isBefore(today)) {
			throw ApiException.badRequest("That date is in the past.");
		}

		// Validates existence/active/junction AND gives us duration + buffer
		// straight from the DB (Trap 4: duration is never client-supplied).
		int needed = availabilityService.validateAndLoadServiceDuration(req.serviceId(), req.stylistId());

		LocalTime start = req.time();
		LocalTime end = start.plusMinutes(needed);
		if (!end.isAfter(start)) {
			throw ApiException.badRequest("This appointment would end after midnight — please pick an earlier time.");
		}

		// ---- Trap 3 critical section: lock, re-check, insert ----
		bookingRepository.lockConfirmedBookingsForDate(req.stylistId(), req.date(), BookingStatus.CONFIRMED);

		boolean free = availabilityService
				.computeFreeSlots(req.stylistId(), req.date(), needed, LocalDateTime.now(), null)
				.stream()
				.anyMatch(slot -> slot.start().equals(start));
		if (!free) {
			// Same message for "outside working hours", "in the past" and
			// "someone just took it" — the frontend reloads slots either way.
			throw ApiException.conflict("This time slot is no longer available.");
		}

		BookingEntity booking = new BookingEntity();
		booking.setServiceId(req.serviceId());
		booking.setStylistId(req.stylistId());
		booking.setBookingDate(req.date());
		booking.setBookingTime(start);
		booking.setEndTime(end);                       // server-computed (Trap 4)
		booking.setStatus(BookingStatus.CONFIRMED);
		booking.setCustomerName(req.customerName().trim());
		booking.setPhone(req.phone().trim());
		booking.setEmail(normalize(owner.email())); // authoritative: from the verified token
		booking.setNotes(req.notes() != null ? req.notes().trim() : null);
		booking.setCreatedAt(LocalDateTime.now());

		try {
			bookingRepository.save(booking);           // UQ_bookings_slot backstop
		} catch (DataIntegrityViolationException ex) {
			// Lost a create race (empty-day lock gap): any constraint violation
			// from this INSERT is UQ_bookings_slot — everything else (ids, NOT
			// NULLs, CHECKs) is validated upstream. Same 409 as the re-check,
			// so the client reloads slots instead of seeing a 500.
			throw ApiException.conflict("This time slot is no longer available.");
		}
		return toResponse(booking);
	}

	@Transactional(readOnly = true)
	public BookingResponse getBooking(Long id, BookingOwner owner) {
		BookingEntity booking = bookingRepository.findById(id)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that booking."));
		requireOwnership(booking, owner, "You can only access your own bookings.");
		return toResponse(booking);
	}

	@Transactional(readOnly = true)
	public List<BookingResponse> listBookingsByEmail(String email, BookingOwner owner) {
		// AUTH PHASE 3: the lookup email must be the caller's own — a logged-in
		// user can never enumerate another customer's bookings by email.
		if (!email.trim().equalsIgnoreCase(owner.email())) {
			throw ApiException.forbidden("You can only access your own bookings.");
		}
		return bookingRepository.findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(email.trim())
				.stream()
				.filter(b -> legacyGateAllows(b, owner)) // PHASE 5A (no-op when verified)
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public BookingResponse cancelBooking(Long id, BookingOwner owner) {
		BookingEntity booking = bookingRepository.findById(id)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that booking."));
		requireOwnership(booking, owner, "You can only cancel your own bookings.");

		if (booking.getStatus() == BookingStatus.CANCELLED) {
			return toResponse(booking); // idempotent: cancelling twice is fine
		}
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw ApiException.conflict("This booking is already " + booking.getStatus().name().toLowerCase()
					+ " and can no longer be cancelled.");
		}
		booking.setStatus(BookingStatus.CANCELLED); // frees the slot instantly
		return toResponse(booking);
	}

	@Transactional
	public BookingResponse rescheduleBooking(Long id, RescheduleRequest req, BookingOwner owner) {
		BookingEntity booking = bookingRepository.findById(id)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that booking."));
		requireOwnership(booking, owner, "You can only reschedule your own bookings.");

		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw ApiException.conflict("Only confirmed bookings can be rescheduled.");
		}
		if (req.date().isBefore(LocalDate.now())) {
			throw ApiException.badRequest("That date is in the past.");
		}

		int needed = availabilityService.validateAndLoadServiceDuration(booking.getServiceId(), booking.getStylistId());
		LocalTime start = req.time();
		LocalTime end = start.plusMinutes(needed);
		if (!end.isAfter(start)) {
			throw ApiException.badRequest("This appointment would end after midnight — please pick an earlier time.");
		}

		// ---- Trap 3 critical section (exclude this booking from the check) ----
		bookingRepository.lockConfirmedBookingsForDate(booking.getStylistId(), req.date(), BookingStatus.CONFIRMED);

		boolean free = availabilityService
				.computeFreeSlots(booking.getStylistId(), req.date(), needed, LocalDateTime.now(), booking.getId())
				.stream()
				.anyMatch(slot -> slot.start().equals(start));
		if (!free) {
			throw ApiException.conflict("This time slot is no longer available.");
		}

		booking.setBookingDate(req.date());
		booking.setBookingTime(start);
		booking.setEndTime(end); // recomputed server-side

		try {
			// The UPDATE is deferred for a managed entity, so without an explicit
			// flush the constraint violation would surface at COMMIT — outside
			// this method — as a raw 500. Flushing here keeps it catchable.
			bookingRepository.saveAndFlush(booking);
		} catch (DataIntegrityViolationException ex) {
			// Lost the empty-target-day race (lock query finds 0 rows to lock, so
			// two reschedules can both pass the re-check; the loser dies at
			// UQ_bookings_slot). Any DIVE here is that index — everything else is
			// validated upstream. Same 409 as the re-check.
			throw ApiException.conflict("This time slot is no longer available.");
		}
		return toResponse(booking);
	}

	// ---------- helpers ----------

	/** AUTH PHASE 3: a booking is visible/mutable only to its own account.
	 *  PHASE 5A adds the legacy gate: an UNVERIFIED account is denied every
		booking created before the account itself — the same generic message
		keeps row existence from leaking. Verified accounts pass unchanged. */
	private void requireOwnership(BookingEntity booking, BookingOwner owner, String message) {
		if (!booking.getEmail().trim().equalsIgnoreCase(owner.email()) || !legacyGateAllows(booking, owner)) {
			throw ApiException.forbidden(message);
		}
	}

	/** PHASE 5A: legacy (pre-account) bookings are hidden from unverified
	 *  accounts. The account's own bookings (created at/after signup) are
	 *  always allowed — new bookings must remain immediately accessible. */
	private boolean legacyGateAllows(BookingEntity booking, BookingOwner owner) {
		return owner.verified()
				|| booking.getCreatedAt() == null
				|| !booking.getCreatedAt().isBefore(owner.accountCreatedAt());
	}

	private String normalize(String email) {
		return email.trim().toLowerCase();
	}

	private BookingResponse toResponse(BookingEntity b) {
		ServiceEntity service = serviceRepository.findById(b.getServiceId()).orElse(null);
		StylistEntity stylist = stylistRepository.findById(b.getStylistId()).orElse(null);
		return BookingResponse.from(b, service, stylist);
	}
}
