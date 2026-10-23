package com.salon.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.salon.dto.ReviewRequest;
import com.salon.dto.ReviewResponse;
import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.ReviewEntity;
import com.salon.exception.ApiException;
import com.salon.repository.BookingRepository;
import com.salon.repository.ReviewRepository;
import com.salon.repository.ServiceRepository;
import com.salon.repository.StylistRepository;
import com.salon.service.BookingService.BookingOwner;

/**
 * PHASE 4 — reviews over the existing schema.sql §7 (no schema change).
 *
 * <p><b>Eligibility (approved HYBRID rule):</b> a booking may be reviewed
 * when {@code (status = CONFIRMED AND the appointment has already ended)}
 * OR {@code status = COMPLETED} (the salon's manual flip in SSMS). Future /
 * active, cancelled and no-show bookings are rejected with a clean 409.
 *
 * <p><b>Ownership + legacy gate:</b> exactly the same rules as cancel/
 * reschedule — the caller must own the booking by email, and the Phase-5A
 * gate denies unverified accounts their pre-account bookings — both with
 * the SAME generic 403 so row existence never leaks.
 *
 * <p><b>Duplicates:</b> one review per booking, enforced by
 * {@code UQ_reviews_booking}; the pre-check gives the friendly 409 and the
 * unique-index race is caught and mapped to the same 409.
 */
@Service
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final BookingRepository bookingRepository;
	private final ServiceRepository serviceRepository;
	private final StylistRepository stylistRepository;

	public ReviewService(ReviewRepository reviewRepository, BookingRepository bookingRepository,
			ServiceRepository serviceRepository, StylistRepository stylistRepository) {
		this.reviewRepository = reviewRepository;
		this.bookingRepository = bookingRepository;
		this.serviceRepository = serviceRepository;
		this.stylistRepository = stylistRepository;
	}

	/** Max reviews returned per request (honest, bounded pagination). */
	private static final int MAX_LIMIT = 50;
	private static final int DEFAULT_LIMIT = 12;

	/** PUBLIC: published reviews, newest first. No auth, no PII beyond the
	 *  masked guest name. Empty table → honest {@code []} (frontend shows an
	 *  empty state — never fabricated testimonials). */
	public List<ReviewResponse> listReviews(Integer limit) {
		int capped = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
				.limit(capped)
				.map(this::toResponse)
				.toList();
	}

	/** AUTHENTICATED: review your own genuinely-completed booking (hybrid
	 *  rule above). Every failure is a clean 4xx with no internals. */
	public ReviewResponse createReview(ReviewRequest request, BookingOwner owner) {
		BookingEntity booking = bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> ApiException.notFound("Not found."));

		// Ownership + Phase-5A legacy gate — ONE generic message (no leak).
		if (!booking.getEmail().trim().equalsIgnoreCase(owner.email().trim())
				|| !legacyGateAllows(booking, owner)) {
			throw ApiException.forbidden("You can only review your own bookings.");
		}

		// Duplicate fast-path (DB unique index is the final net below).
		if (reviewRepository.existsByBookingId(booking.getId())) {
			throw ApiException.conflict("This booking already has a review.");
		}

		// HYBRID eligibility.
		if (booking.getStatus() == BookingStatus.CANCELLED
				|| booking.getStatus() == BookingStatus.NO_SHOW) {
			throw ApiException.conflict("Only completed visits can be reviewed.");
		}
		boolean manuallyCompleted = booking.getStatus() == BookingStatus.COMPLETED;
		boolean pastConfirmed = booking.getStatus() == BookingStatus.CONFIRMED
				&& java.time.LocalDateTime.of(booking.getBookingDate(), booking.getEndTime())
						.isBefore(LocalDateTime.now());
		if (!manuallyCompleted && !pastConfirmed) {
			throw ApiException.conflict(
					"This booking isn't finished yet — reviews open once the appointment ends.");
		}

		ReviewEntity review = new ReviewEntity();
		review.setBookingId(booking.getId());
		review.setRating(request.rating().byteValue());
		String comment = request.comment() == null ? null : request.comment().trim();
		review.setComment(comment == null || comment.isEmpty() ? null : comment);
		review.setCreatedAt(LocalDateTime.now());

		try {
			reviewRepository.saveAndFlush(review);
		} catch (DataIntegrityViolationException e) {
			// UQ_reviews_booking raced a concurrent duplicate — same honest 409.
			throw ApiException.conflict("This booking already has a review.");
		}
		return toResponse(review);
	}

	/** Same semantics as BookingService.legacyGateAllows (PHASE 5A): an
	 *  unverified account never reaches bookings created before itself. */
	private boolean legacyGateAllows(BookingEntity booking, BookingOwner owner) {
		return owner.verified()
				|| booking.getCreatedAt() == null
				|| !booking.getCreatedAt().isBefore(owner.accountCreatedAt());
	}

	private ReviewResponse toResponse(ReviewEntity r) {
		BookingEntity booking = bookingRepository.findById(r.getBookingId()).orElse(null);
		String serviceName = "";
		String stylistName = "";
		String guestName = "Guest";
		if (booking != null) {
			serviceName = serviceRepository.findById(booking.getServiceId())
					.map(s -> s.getName()).orElse("");
			stylistName = stylistRepository.findById(booking.getStylistId())
					.map(s -> s.getName()).orElse("");
			guestName = maskName(booking.getCustomerName());
		}
		return new ReviewResponse(
				r.getId(),
				r.getBookingId(),
				r.getRating() == null ? 0 : r.getRating().intValue(),
				r.getComment(),
				guestName,
				serviceName,
				stylistName,
				r.getCreatedAt() == null ? null : r.getCreatedAt().toLocalDate().toString());
	}

	/** PRIVACY: reviews are public — publish only "First L." style names,
	 *  never the full customer name stored on the booking. */
	static String maskName(String fullName) {
		if (fullName == null || fullName.isBlank()) {
			return "Guest";
		}
		String[] parts = fullName.trim().split("\\s+");
		if (parts.length == 1) {
			return parts[0];
		}
		String surname = parts[parts.length - 1];
		return parts[0] + " " + Character.toUpperCase(surname.charAt(0)) + ".";
	}
}
