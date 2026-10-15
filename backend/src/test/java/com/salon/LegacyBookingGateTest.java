package com.salon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.UserEntity;
import com.salon.repository.BookingRepository;
import com.salon.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * PHASE 5A — legacy-booking gate tests.
 *
 * Scenario: a booking row exists whose email matches a NEW account's email
 * but which was created BEFORE the account (inserted directly via the
 * repository — it simulates a pre-auth booking). The gate must hide it
 * from the unverified account everywhere (list / get / cancel / reschedule)
 * while the account's OWN booking stays fully accessible. Flipping
 * email_verified=1 (simulating salon verification) must expose it again.
 *
 * Self-cleaning: both the legacy row and the account are created + deleted
 * here; no real booking or user is touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyBookingGateTest {

	private static final String LEGACY_EMAIL = "legacygate@example.com";
	private static final String PASSWORD = "LegacyPass1";
	private static final String NAME = "Legacy Gate";

	/** First Monday after today+1yr (same convention as the booking tests). */
	private static final LocalDate FUTURE_DATE = LocalDate.now().plusYears(1)
			.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	/**
	 * Static on purpose: JUnit creates a fresh test-instance per @Test method,
	 * so an instance field would make setupFixture() insert the legacy row again
	 * for every method and trip UQ_bookings_slot. Sharing the id keeps the
	 * fixture inserted exactly once for the class.
	 */
	private static Long legacyId;

	private String loginJson() {
		return "{\"email\": \"" + LEGACY_EMAIL + "\", \"password\": \"" + PASSWORD + "\"}";
	}

	/** Insert a pre-auth booking + unverified account (legacy BEFORE account). */
	private void setupFixture() {
		if (legacyId != null) { return; }
		entityManager.clear();

		// Self-heal: if a previous run died before @AfterAll, leftover rows for
		// this fixture would collide with the unique slot index — clear them.
		var leftoverBookings = bookingRepository
				.findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(LEGACY_EMAIL);
		if (!leftoverBookings.isEmpty()) {
			bookingRepository.deleteAll(leftoverBookings);
			bookingRepository.flush();
		}
		UserEntity leftoverAccount = userRepository.findByEmailIgnoreCase(LEGACY_EMAIL).orElse(null);
		if (leftoverAccount != null) {
			userRepository.delete(leftoverAccount);
			userRepository.flush();
		}

		BookingEntity legacy = new BookingEntity();
		legacy.setServiceId(1L);
		legacy.setStylistId(1L);
		legacy.setBookingDate(FUTURE_DATE);
		legacy.setBookingTime(LocalTime.of(9, 0));
		legacy.setEndTime(LocalTime.of(9, 55));
		legacy.setStatus(BookingStatus.CONFIRMED);
		legacy.setCustomerName("Pre Auth Customer");
		legacy.setPhone("0700000011");
		legacy.setEmail(LEGACY_EMAIL);
		legacy.setCreatedAt(LocalDateTime.now().minusDays(2)); // pre-dates the account
		legacyId = bookingRepository.saveAndFlush(legacy).getId();

		UserEntity account = new UserEntity();
		account.setName(NAME);
		account.setEmail(LEGACY_EMAIL);
		account.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD));
		account.setEmailVerified(false);
		account.setCreatedAt(LocalDateTime.now().minusDays(1)); // AFTER the legacy booking
		userRepository.saveAndFlush(account);
	}

	private String bearer() throws Exception {
		setupFixture();
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content(loginJson()))
				.andExpect(status().isOk()).andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		org.assertj.core.api.Assertions.assertThat(body.path("verified").asBoolean()).isFalse();
		return "Bearer " + body.path("token").asString();
	}

	@Test
	@Order(1)
	void me_reportsVerifiedFalse() throws Exception {
		setupFixture();
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(false));
		mockMvc.perform(get("/api/auth/me").header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(false))
				.andExpect(jsonPath("$.email").value(LEGACY_EMAIL));
	}

	@Test
	@Order(2)
	void list_hidesLegacyRow_butShowsNewBooking() throws Exception {
		String auth = bearer();

		// the account books something NOW (own booking — must be visible)
		String own = "{\"serviceId\": 1, \"stylistId\": 1, \"date\": \"" + FUTURE_DATE
				+ "\", \"time\": \"11:00\", \"customerName\": \"" + NAME
				+ "\", \"phone\": \"0700000011\", \"email\": \"" + LEGACY_EMAIL
				+ "\", \"notes\": \"own booking\"}";
		mockMvc.perform(post("/api/bookings").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(own))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(get("/api/bookings").param("email", LEGACY_EMAIL)
				.header("Authorization", auth))
				.andExpect(status().isOk()).andReturn();
		JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());

		// own booking visible, legacy row hidden
		org.assertj.core.api.Assertions.assertThat(list).anySatisfy(
				b -> org.assertj.core.api.Assertions.assertThat(b.path("time").asString()).isEqualTo("11:00"));
		org.assertj.core.api.Assertions.assertThat(list).noneSatisfy(
				b -> org.assertj.core.api.Assertions.assertThat(b.path("time").asString()).isEqualTo("09:00"));
	}

	@Test
	@Order(3)
	void legacyRow_getCancelReschedule_allDenied() throws Exception {
		String auth = bearer();

		mockMvc.perform(get("/api/bookings/" + legacyId).header("Authorization", auth))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("You can only access your own bookings."));
		mockMvc.perform(put("/api/bookings/" + legacyId + "/cancel").header("Authorization", auth))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/bookings/" + legacyId + "/reschedule").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\": \"" + FUTURE_DATE + "\", \"time\": \"15:00\"}"))
				.andExpect(status().isForbidden());

		// the legacy row is untouched by every denied attempt
		org.assertj.core.api.Assertions.assertThat(
				bookingRepository.findById(legacyId).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.CONFIRMED);
	}

	@Test
	@Order(4)
	void verification_exposesLegacyRow_fullFlowWorks() throws Exception {
		String auth = bearer();

		// salon verifies the address → flag flips (admin action)
		UserEntity account = userRepository.findByEmailIgnoreCase(LEGACY_EMAIL).orElseThrow();
		account.setEmailVerified(true);
		userRepository.saveAndFlush(account);
		entityManager.clear();

		// a fresh login token now carries verified=true → legacy row flows end-to-end
		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content(loginJson()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(true)).andReturn();
		String fresh = "Bearer " + objectMapper
				.readTree(login.getResponse().getContentAsString()).path("token").asString();

		mockMvc.perform(get("/api/bookings/" + legacyId).header("Authorization", fresh))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.time").value("09:00"));
		mockMvc.perform(put("/api/bookings/" + legacyId + "/cancel").header("Authorization", fresh))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		// cross-user protection unchanged (Phase 3): another email → 403
		mockMvc.perform(get("/api/bookings").param("email", "other@example.com").header("Authorization", fresh))
				.andExpect(status().isForbidden());
	}

	@AfterAll
	static void cleanup(@Autowired BookingRepository bookingRepository,
			@Autowired UserRepository userRepository,
			@Autowired EntityManager entityManager) {
		entityManager.clear();
		bookingRepository.deleteAll(bookingRepository
				.findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(LEGACY_EMAIL));
		UserEntity account = userRepository.findByEmailIgnoreCase(LEGACY_EMAIL).orElse(null);
		if (account != null) {
			userRepository.delete(account);
		}
		bookingRepository.flush();
		userRepository.flush();
		System.out.println("[LegacyBookingGateTest] cleaned up legacy row + account for " + LEGACY_EMAIL);
	}
}
