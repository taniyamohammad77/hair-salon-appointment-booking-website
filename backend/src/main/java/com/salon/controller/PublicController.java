package com.salon.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salon.dto.BookingRequest;
import com.salon.dto.BookingResponse;
import com.salon.dto.RescheduleRequest;
import com.salon.dto.ReviewRequest;
import com.salon.dto.ReviewResponse;
import com.salon.dto.ServiceDto;
import com.salon.dto.StylistDto;
import com.salon.service.AuthService;
import com.salon.service.AvailabilityService;
import com.salon.service.BookingService;
import com.salon.service.BookingService.BookingOwner;
import com.salon.service.ReviewService;
import com.salon.service.ServiceCatalogService;
import com.salon.service.StylistCatalogService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

/**
 * The ONLY controller in this system — public booking, no admin page (user
 * decision). All responses are JSON under /api/**.
 */
@RestController
@RequestMapping("/api")
public class PublicController {

	private final ServiceCatalogService serviceCatalogService;
	private final StylistCatalogService stylistCatalogService;
	private final AvailabilityService availabilityService;
	private final BookingService bookingService;
	private final ReviewService reviewService;
	private final AuthService authService;

	public PublicController(ServiceCatalogService serviceCatalogService,
			StylistCatalogService stylistCatalogService,
			AvailabilityService availabilityService,
			BookingService bookingService,
			ReviewService reviewService,
			AuthService authService) {
		this.serviceCatalogService = serviceCatalogService;
		this.stylistCatalogService = stylistCatalogService;
		this.availabilityService = availabilityService;
		this.bookingService = bookingService;
		this.reviewService = reviewService;
		this.authService = authService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "up");
	}

	// ---------- catalogue ----------

	@GetMapping("/services")
	public List<ServiceDto> listServices() {
		return serviceCatalogService.listActiveServices();
	}

	@GetMapping("/services/{id}")
	public ServiceDto getService(@PathVariable Long id) {
		return serviceCatalogService.getActiveService(id);
	}

	@GetMapping("/stylists")
	public List<StylistDto> listStylists(@RequestParam(required = false) Long serviceId) {
		return stylistCatalogService.listStylists(serviceId);
	}

	// ---------- availability ----------

	/**
	 * Free start times for a stylist+service+date, e.g. ["10:00","10:15",...].
	 */
	@GetMapping("/availability")
	public Map<String, Object> getAvailability(
			@RequestParam Long serviceId,
			@RequestParam Long stylistId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

		if (date.isBefore(LocalDate.now())) {
			throw com.salon.exception.ApiException.badRequest("Date is in the past.");
		}
		List<String> slots = availabilityService.getAvailableSlots(serviceId, stylistId, date);
		return Map.of(
				"date", date.toString(),
				"stylistId", stylistId,
				"serviceId", serviceId,
				"slots", slots);
	}

	// ---------- bookings (AUTH PHASE 3 — account required + ownership) ----------

	/** AUTH PHASE 3: booking requires a logged-in account (approved decision
	 *  — no guest booking). Missing/invalid/expired token → 401. */
	@PostMapping("/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse createBooking(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody BookingRequest request) {
		return bookingService.createBooking(request, requireOwner(authorization));
	}

	@GetMapping("/bookings/{id}")
	public BookingResponse getBooking(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@PathVariable Long id) {
		return bookingService.getBooking(id, requireOwner(authorization));
	}

	/** "My appointments" — looked up by the customer's email, which must be
	 *  the authenticated user's own (403 otherwise; no cross-user reads). */
	@GetMapping("/bookings")
	public List<BookingResponse> listBookings(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam @Email @jakarta.validation.constraints.NotBlank String email) {
		return bookingService.listBookingsByEmail(email, requireOwner(authorization));
	}

	@PutMapping("/bookings/{id}/cancel")
	public BookingResponse cancelBooking(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@PathVariable Long id) {
		return bookingService.cancelBooking(id, requireOwner(authorization));
	}

	@PutMapping("/bookings/{id}/reschedule")
	public BookingResponse rescheduleBooking(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
		return bookingService.rescheduleBooking(id, request, requireOwner(authorization));
	}

	// ---------- reviews (PHASE 4) ----------

	/** PHASE 4 — PUBLIC: published reviews, newest first. {@code limit} is
	 *  clamped server-side (1–50, default 12); an empty table returns an
	 *  honest {@code []} — the frontend shows an empty state, never fake
	 *  testimonials. No token required (same class as the catalogue GETs). */
	@GetMapping("/reviews")
	public List<ReviewResponse> listReviews(@RequestParam(required = false) Integer limit) {
		return reviewService.listReviews(limit);
	}

	/** PHASE 4 — AUTHENTICATED: review your OWN genuinely-completed booking.
	 *  Hybrid rule: the appointment has already ended (CONFIRMED) or the
	 *  salon marked it COMPLETED. 401 no/invalid token · 403 not your booking
	 *  (same generic message as the legacy gate — no existence leak) · 409
	 *  not finished / cancelled / already reviewed · 400 bad rating/comment. */
	@PostMapping("/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public ReviewResponse createReview(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Valid @RequestBody ReviewRequest request) {
		return reviewService.createReview(request, requireOwner(authorization));
	}

	/** Resolves the Bearer token → the account ENTITY (verified flag +
	 *  createdAt feed the PHASE 5A ownership gate); 401 on any failure. */
	private BookingOwner requireOwner(String authorization) {
		return BookingOwner.from(authService.currentUserEntity(authorization));
	}
}
