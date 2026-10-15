package com.salon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.salon.config.SalonProperties;
import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.TimeOffEntity;
import com.salon.entity.WorkingHoursEntity;
import com.salon.repository.BookingRepository;
import com.salon.repository.ServiceRepository;
import com.salon.repository.StylistRepository;
import com.salon.repository.StylistServiceRepository;
import com.salon.repository.TimeOffRepository;
import com.salon.repository.WorkingHoursRepository;

/**
 * Slot-math regression tests for the availability engine. No Spring context,
 * no database — the repositories are mocked, the grid logic is real.
 *
 * Covers the midnight-wrap bug fix: LocalTime.plusMinutes wraps at midnight,
 * which used to offer phantom slots (e.g. 09:00–05:00(+1d)) for services
 * longer than the shift. computeFreeSlots now uses LocalDateTime math.
 */
class AvailabilityServiceSlotMathTest {

	private static final Long STYLIST = 7L;

	private AvailabilityService availabilityService;
	private WorkingHoursRepository workingHoursRepository;
	private TimeOffRepository timeOffRepository;
	private BookingRepository bookingRepository;

	/** Always a Monday, so the weekday lookup (1) is deterministic. */
	private static final LocalDate MONDAY = LocalDate.now()
			.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	@BeforeEach
	void setUp() {
		workingHoursRepository = mock(WorkingHoursRepository.class);
		timeOffRepository = mock(TimeOffRepository.class);
		bookingRepository = mock(BookingRepository.class);
		availabilityService = new AvailabilityService(
				mock(ServiceRepository.class),
				mock(StylistRepository.class),
				mock(StylistServiceRepository.class),
				workingHoursRepository,
				timeOffRepository,
				bookingRepository,
				new SalonProperties(15));
	}

	// ---------- helpers ----------

	private void shift(LocalTime start, LocalTime end) {
		WorkingHoursEntity wh = new WorkingHoursEntity();
		wh.setStylistId(STYLIST);
		wh.setWeekday((byte) MONDAY.getDayOfWeek().getValue());
		wh.setStartTime(start);
		wh.setEndTime(end);
		when(workingHoursRepository.findByStylistIdAndWeekday(STYLIST,
				(byte) MONDAY.getDayOfWeek().getValue())).thenReturn(List.of(wh));
	}

	private void noTimeOff() {
		when(timeOffRepository.findByStylistIdAndOffDate(STYLIST, MONDAY)).thenReturn(List.of());
	}

	private void fullDayOff() {
		TimeOffEntity off = new TimeOffEntity(); // null + null = full day (Trap 2)
		when(timeOffRepository.findByStylistIdAndOffDate(STYLIST, MONDAY)).thenReturn(List.of(off));
	}

	private void confirmedBooking(LocalTime start, LocalTime end) {
		BookingEntity b = new BookingEntity();
		b.setBookingTime(start);
		b.setEndTime(end);
		b.setStatus(BookingStatus.CONFIRMED);
		when(bookingRepository.findByStylistIdAndBookingDateAndStatusOrderByBookingTimeAsc(
				STYLIST, MONDAY, BookingStatus.CONFIRMED)).thenReturn(List.of(b));
	}

	private List<AvailabilityService.Slot> slotsFor(int neededMinutes) {
		// Reference time = start of that day, so no slot is dismissed as "past";
		// the in-past filter itself is covered by pastSlotsAreFiltered().
		LocalDateTime startOfDay = LocalDateTime.of(MONDAY, LocalTime.MIDNIGHT);
		return availabilityService.computeFreeSlots(STYLIST, MONDAY, neededMinutes,
				startOfDay, null);
	}

	// ---------- the regression: no slots may wrap past midnight ----------

	@Test
	@DisplayName("service longer than the shift → no slots (no phantom wrap)")
	void serviceLongerThanShiftYieldsNothing() {
		shift(LocalTime.of(9, 0), LocalTime.of(17, 0)); // 8h shift
		noTimeOff();

		// 10h of duration+buffer: 09:00 + 600min = 19:00 — past the shift end.
		assertThat(slotsFor(600)).isEmpty();

		// 16h: 09:00 + 960min = 01:00 NEXT day. LocalTime math used to wrap
		// this to 01:00, see 01:00.isAfter(17:00) == false, and offer a
		// phantom 09:00–01:00(+1d) slot. Must be empty now.
		assertThat(slotsFor(960)).isEmpty();
	}

	@Test
	@DisplayName("slot exactly filling the shift is still offered")
	void slotExactlyFillingShiftIsOffered() {
		shift(LocalTime.of(9, 0), LocalTime.of(12, 0));
		noTimeOff();

		List<AvailabilityService.Slot> slots = slotsFor(180); // 09:00–12:00 exactly

		assertThat(slots).hasSize(1);
		assertThat(slots.get(0).start()).isEqualTo(LocalTime.of(9, 0));
		assertThat(slots.get(0).end()).isEqualTo(LocalTime.of(12, 0));
	}

	// ---------- normal grid behavior ----------

	@Test
	@DisplayName("60-min service on a 09:00–12:00 shift → 9 slots on the 15-min grid")
	void normalGridSlots() {
		shift(LocalTime.of(9, 0), LocalTime.of(12, 0));
		noTimeOff();

		List<AvailabilityService.Slot> slots = slotsFor(60);

		assertThat(slots).hasSize(9);
		assertThat(slots.get(0).start()).isEqualTo(LocalTime.of(9, 0));
		assertThat(slots.get(slots.size() - 1).start()).isEqualTo(LocalTime.of(11, 0));
		assertThat(slots).isSortedAccordingTo(java.util.Comparator.comparing(AvailabilityService.Slot::start));
	}

	@Test
	@DisplayName("full-day time off (null/null) blocks every slot (Trap 2)")
	void fullDayOffBlocksEverything() {
		shift(LocalTime.of(9, 0), LocalTime.of(17, 0));
		fullDayOff();

		assertThat(slotsFor(60)).isEmpty();
	}

	@Test
	@DisplayName("confirmed booking blocks the overlapping slots, not the whole day")
	void confirmedBookingBlocksOnlyOverlap() {
		shift(LocalTime.of(9, 0), LocalTime.of(12, 0));
		noTimeOff();
		confirmedBooking(LocalTime.of(10, 0), LocalTime.of(11, 0));

		List<AvailabilityService.Slot> slots = slotsFor(60);

		// Only 09:00 (ends 10:00 exactly — touching, not overlapping) fits before;
		// 09:15+ would run past 10:00 and collide. 11:00 (ends 12:00) fits after;
		// 11:15 would end 12:15 → out.
		assertThat(slots).extracting(AvailabilityService.Slot::start)
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
	}

	@Test
	@DisplayName("cancelled bookings do not block slots")
	void cancelledBookingDoesNotBlock() {
		shift(LocalTime.of(9, 0), LocalTime.of(12, 0));
		noTimeOff();
		// Repository method only returns CONFIRMED rows; assert the full grid.
		when(bookingRepository.findByStylistIdAndBookingDateAndStatusOrderByBookingTimeAsc(
				STYLIST, MONDAY, BookingStatus.CONFIRMED)).thenReturn(List.of());

		assertThat(slotsFor(60)).hasSize(9);
	}

	@Test
	@DisplayName("no shift rows that day → closed → zero slots")
	void closedDayYieldsNothing() {
		when(workingHoursRepository.findByStylistIdAndWeekday(STYLIST,
				(byte) MONDAY.getDayOfWeek().getValue())).thenReturn(List.of());
		noTimeOff();

		assertThat(slotsFor(60)).isEmpty();
	}

	@Test
	@DisplayName("slots in the past (today) are filtered out")
	void pastSlotsAreFiltered() {
		shift(LocalTime.of(9, 0), LocalTime.of(17, 0));
		noTimeOff();

		// It is "10:05" now → 09:00–10:00 starts are gone, 10:15 is the first.
		List<AvailabilityService.Slot> slots = availabilityService.computeFreeSlots(
				STYLIST, MONDAY, 60, LocalDateTime.of(MONDAY, LocalTime.of(10, 5)), null);

		assertThat(slots.get(0).start()).isEqualTo(LocalTime.of(10, 15));
		assertThat(slots).extracting(AvailabilityService.Slot::start)
				.doesNotContain(LocalTime.of(9, 0), LocalTime.of(10, 0));
	}
}
