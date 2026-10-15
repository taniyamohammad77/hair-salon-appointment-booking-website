package com.salon.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salon.config.SalonProperties;
import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.TimeOffEntity;
import com.salon.entity.WorkingHoursEntity;
import com.salon.exception.ApiException;
import com.salon.repository.BookingRepository;
import com.salon.repository.ServiceRepository;
import com.salon.repository.StylistRepository;
import com.salon.repository.StylistServiceRepository;
import com.salon.repository.TimeOffRepository;
import com.salon.repository.WorkingHoursRepository;

/**
 * THE AVAILABILITY ENGINE (ER-DIAGRAM section 5):
 *
 *   free slots(stylist, date) =
 *         working_hours rows for (stylist, weekday(date))    -- when they CAN work
 *       MINUS time_off overlapping that date                 -- when they CAN'T
 *       MINUS existing CONFIRMED bookings that day           -- already taken
 *       sliced by duration + buffer                          -- what FITS
 *
 * Every slot it returns is real (rule 10) — and the same interval-overlap
 * logic is re-checked inside the booking transaction (Trap 1 defense in
 * depth, see BookingService).
 */
@Service
public class AvailabilityService {

	/** One candidate slot: start + end (end = start + duration + buffer). */
	public record Slot(LocalTime start, LocalTime end) {
	}

	private final ServiceRepository serviceRepository;
	private final StylistRepository stylistRepository;
	private final StylistServiceRepository stylistServiceRepository;
	private final WorkingHoursRepository workingHoursRepository;
	private final TimeOffRepository timeOffRepository;
	private final BookingRepository bookingRepository;
	private final SalonProperties salonProperties;

	public AvailabilityService(ServiceRepository serviceRepository,
			StylistRepository stylistRepository,
			StylistServiceRepository stylistServiceRepository,
			WorkingHoursRepository workingHoursRepository,
			TimeOffRepository timeOffRepository,
			BookingRepository bookingRepository,
			SalonProperties salonProperties) {
		this.serviceRepository = serviceRepository;
		this.stylistRepository = stylistRepository;
		this.stylistServiceRepository = stylistServiceRepository;
		this.workingHoursRepository = workingHoursRepository;
		this.timeOffRepository = timeOffRepository;
		this.bookingRepository = bookingRepository;
		this.salonProperties = salonProperties;
	}

	/**
	 * All free start times for one stylist+service+date, sorted.
	 */
	@Transactional(readOnly = true)
	public List<String> getAvailableSlots(Long serviceId, Long stylistId, LocalDate date) {
		int needed = validateAndLoadServiceDuration(serviceId, stylistId);
		List<Slot> slots = computeFreeSlots(stylistId, date, needed, LocalDateTime.now(), null);
		return slots.stream().map(s -> s.start().toString()).toList();
	}

	/**
	 * Slot objects (with ends) — reused by BookingService for validation.
	 *
	 * @param neededMinutes   service duration + buffer
	 * @param now             "earliest allowed start" reference time
	 * @param excludeBookingId booking to ignore (reschedule must not be
	 *                        blocked by its own current interval); null = none
	 */
	@Transactional(readOnly = true)
	public List<Slot> computeFreeSlots(Long stylistId, LocalDate date, int neededMinutes,
			LocalDateTime now, Long excludeBookingId) {
		// ISO: 1 = Monday ... 7 = Sunday — matches the DB TINYINT values
		Byte weekday = (byte) date.getDayOfWeek().getValue();

		// 1. When they CAN work (no shift rows that day -> closed)
		List<WorkingHoursEntity> shifts = workingHoursRepository.findByStylistIdAndWeekday(stylistId, weekday);
		if (shifts.isEmpty()) {
			return List.of();
		}

		// 2. When they CAN'T: time off — TRAP 2: null/null = FULL DAY,
		//    normalized to 00:00–24:00 so it blocks every slot.
		List<Interval> blocked = new ArrayList<>();
		for (TimeOffEntity off : timeOffRepository.findByStylistIdAndOffDate(stylistId, date)) {
			LocalTime from = off.getStartTime() != null ? off.getStartTime() : LocalTime.MIN;
			LocalTime to = off.getEndTime() != null ? off.getEndTime() : LocalTime.MAX;
			blocked.add(new Interval(from, to));
		}

		// 3. Already taken: CONFIRMED bookings only (cancelled/completed/no-show
		//    free the slot). Interval = booking_time .. end_time, where end_time
		//    already contains duration + buffer (server-computed at save).
		for (BookingEntity b : bookingRepository
				.findByStylistIdAndBookingDateAndStatusOrderByBookingTimeAsc(stylistId, date, BookingStatus.CONFIRMED)) {
			if (excludeBookingId != null && excludeBookingId.equals(b.getId())) {
				continue;
			}
			blocked.add(new Interval(b.getBookingTime(), b.getEndTime()));
		}

		// 4. Walk the grid and keep slots that fit and don't collide.
		//    LocalDateTime math (NOT LocalTime): LocalTime.plusMinutes wraps at
		// midnight, so a huge duration+buffer used to "wrap" the shift end and
			// offer phantom slots like 09:00–05:00(+1d). Here a slot must end on
			// the SAME day inside the shift, or it is simply never offered.
		int step = salonProperties.slotStepMinutes();
		List<Slot> free = new ArrayList<>();
		LocalDateTime earliestStart = now; // never offer slots in the past

		for (WorkingHoursEntity shift : shifts) {
			LocalDateTime shiftEnd = LocalDateTime.of(date, shift.getEndTime());
			LocalDateTime cursor = LocalDateTime.of(date, shift.getStartTime());
			while (!cursor.plusMinutes(neededMinutes).isAfter(shiftEnd)) {
				LocalDateTime slotEnd = cursor.plusMinutes(neededMinutes);
				boolean inPast = cursor.isBefore(earliestStart);
				if (!inPast && isFree(cursor.toLocalTime(), slotEnd.toLocalTime(), blocked)) {
					free.add(new Slot(cursor.toLocalTime(), slotEnd.toLocalTime()));
				}
				cursor = cursor.plusMinutes(step);
			}
		}

		free.sort(Comparator.comparing(Slot::start));
		return free;
	}

	/**
	 * Shared validation: service + stylist must exist, be active and the
	 * stylist must offer the service. Returns duration + buffer in minutes.
	 */
	@Transactional(readOnly = true)
	public int validateAndLoadServiceDuration(Long serviceId, Long stylistId) {
		var service = serviceRepository.findByIdAndActiveTrue(serviceId)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that service."));
		var stylist = stylistRepository.findByIdAndActiveTrue(stylistId)
				.orElseThrow(() -> ApiException.notFound("We couldn't find that stylist."));
		boolean offers = stylistServiceRepository.findByStylistId(stylistId).stream()
				.anyMatch(ss -> ss.getServiceId().equals(serviceId));
		if (!offers) {
			throw ApiException.badRequest("That stylist doesn't offer this service.");
		}
		return service.getDurationMinutes() + service.getBufferMinutes();
	}

	/** Overlap test (Trap 1): [s1,e1) vs [s2,e2) overlap iff s1 < e2 AND s2 < e1. */
	private boolean isFree(LocalTime start, LocalTime end, List<Interval> blocked) {
		for (Interval b : blocked) {
			if (start.isBefore(b.end()) && b.start().isBefore(end)) {
				return false;
			}
		}
		return true;
	}

	private record Interval(LocalTime start, LocalTime end) {
	}
}
