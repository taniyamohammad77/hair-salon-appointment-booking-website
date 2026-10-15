package com.salon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
import com.salon.repository.BookingRepository;
import com.salon.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Full-stack behavior tests: MockMvc → controller → services → JPA → the real
 * local SQL Server (same DB as dev). Requires the DB from scripts/schema.sql;
 * seed rows come from data.sql (idempotent).
 *
 * Fixtures (from data.sql): stylist 1 = Maya (Mon–Fri 09:00–17:00) offers
 * service 1 = Classic Haircut (45 min + 10 buffer = 55 min per booking).
 *
 * AUTH PHASE 3: all booking endpoints now require a logged-in account, and
 * every operation is ownership-checked. This class creates its OWN account
 * through the signup API (fixed test email), books ONLY under that account,
 * and removes the account + bookings in @AfterAll — no rows remain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // real commits, like production
class BookingApiIntegrationTest {

	/** First Monday after today+1yr: Maya works Mondays; no other data lives there. */
	private static final LocalDate FUTURE_DATE = LocalDate.now().plusYears(1)
			.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

	private static final String TEST_EMAIL = "slotmath@example.com";
	private static final String TEST_PASSWORD = "SlotMathPass1";

	private static final String BASE_JSON = """
			{
			  "serviceId": 1,
			  "stylistId": 1,
			  "date": "%s",
			  "time": "10:00",
			  "customerName": "Test Customer",
			  "phone": "0700000000",
			  "email": "slotmath@example.com",
			  "notes": "integration test booking"
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private EntityManager entityManager;

	// ---------- lifecycle ----------

	/** Fails fast if the seed data this class depends on is missing. */
	@Test
	@Order(0)
	void preflight_seedDataExists() throws Exception {
		mockMvc.perform(get("/api/services/1")).andExpect(status().isOk());
		mockMvc.perform(get("/api/stylists?serviceId=1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == 1)]").exists());
	}

	// ---------- auth helpers (AUTH PHASE 3) ----------

	/** Create the account once; later orders reuse it via login. */
	private void signupAccount() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "name": "Slot Math",
						  "email": "%s",
						  "password": "%s"
						}
						""").formatted(TEST_EMAIL, TEST_PASSWORD)))
				// 201 on the first run, 409 on re-runs — both are fine.
				.andExpect(result -> {
					int s = result.getResponse().getStatus();
					org.assertj.core.api.Assertions.assertThat(s).isIn(201, 409);
				});
	}

	private String loginToken() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""").formatted(TEST_EMAIL, TEST_PASSWORD)))
				.andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
	}

	private String bearer() throws Exception {
		signupAccount();
		return "Bearer " + loginToken();
	}

	// ---------- happy path ----------

	@Test
	@Order(1)
	void createBooking_returns201_andComputesEndTime() throws Exception {
		String body = BASE_JSON.formatted(FUTURE_DATE);
		// Classic Haircut: 45 min + 10 buffer → endTime must be 10:55, server-computed.
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.time").value("10:00"))
				.andExpect(jsonPath("$.endTime").value("10:55"))
				.andExpect(jsonPath("$.date").value(FUTURE_DATE.toString()));
	}

	@Test
	@Order(2)
	void availability_excludesBookedSlot() throws Exception {
		// Availability stays PUBLIC (catalog/slots are not customer data).
		MvcResult result = mockMvc
				.perform(get("/api/availability").param("serviceId", "1").param("stylistId", "1")
						.param("date", FUTURE_DATE.toString()))
				.andExpect(status().isOk()).andReturn();
		JsonNode slots = objectMapper.readTree(result.getResponse().getContentAsString()).path("slots");
		assertThatSlotsDoNotContain(slots, "10:00");
		// 10:15/10:30/10:45 all overlap 10:00–10:55 → gone too; 09:00 still there.
		assertThatSlotsDoNotContain(slots, "10:15");
		assertThatSlotsDoNotContain(slots, "10:45");
		assertThatSlotsContain(slots, "09:00");
	}

	// ---------- AUTH PHASE 3: unauthenticated access ----------

	@Test
	@Order(3)
	void bookingEndpoints_withoutToken_return401() throws Exception {
		String body = BASE_JSON.formatted(FUTURE_DATE);

		// create
		mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Authentication is required. Please log in."));
		// by-id read
		mockMvc.perform(get("/api/bookings/1"))
				.andExpect(status().isUnauthorized());
		// by-email list
		mockMvc.perform(get("/api/bookings").param("email", TEST_EMAIL))
				.andExpect(status().isUnauthorized());
		// cancel + reschedule
		mockMvc.perform(put("/api/bookings/1/cancel")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/bookings/1/reschedule").contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\": \"" + FUTURE_DATE + "\", \"time\": \"14:00\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Order(4)
	void bookingEndpoints_withGarbageToken_return401() throws Exception {
		mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer not-a-token")
				.contentType(MediaType.APPLICATION_JSON).content(BASE_JSON.formatted(FUTURE_DATE)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/bookings").param("email", TEST_EMAIL)
				.header("Authorization", "Bearer not-a-token"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- duplicate / overlap / validation ----------

	@Test
	@Order(5)
	void duplicateExactSlot_returns409() throws Exception {
		String body = BASE_JSON.formatted(FUTURE_DATE); // same stylist/date/time
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("This time slot is no longer available."));
	}

	@Test
	@Order(6)
	void overlappingStartInsideExistingBooking_returns409() throws Exception {
		String body = """
				{
				  "serviceId": 1,
				  "stylistId": 1,
				  "date": "%s",
				  "time": "10:05",
				  "customerName": "Overlap Probe",
				  "phone": "0700000001",
				  "email": "slotmath@example.com",
				  "notes": null
				}
				""".formatted(FUTURE_DATE);
		// 10:05 is not on the 15-min grid AND overlaps 10:00–10:55 → conflict.
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
	}

	@Test
	@Order(7)
	void invalidPayloads_return400() throws Exception {
		String pastDate = BASE_JSON.formatted(LocalDate.now().minusDays(1));
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(pastDate))
				.andExpect(status().isBadRequest());

		String badEmail = BASE_JSON.formatted(FUTURE_DATE)
				.replace("\"10:00\"", "\"11:00\"")
				.replace(TEST_EMAIL, "not-an-email");
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(badEmail))
				// format validation (400) fires before the ownership check (403)
				.andExpect(status().isBadRequest());

		String longName = BASE_JSON.formatted(FUTURE_DATE)
				.replace("\"10:00\"", "\"11:00\"")
				.replace("Test Customer", "x".repeat(121)); // DB column is 120
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(longName))
				.andExpect(status().isBadRequest());

		// 158 a's + "@e.co" = 163 chars > @Size(max=160) → 400 BEFORE any DB
		// write (without the DTO limit this would hit varchar(160) → 500).
		String emailTooLong = BASE_JSON.formatted(FUTURE_DATE)
				.replace("\"10:00\"", "\"11:00\"")
				.replace(TEST_EMAIL, "a".repeat(158) + "@e.co");
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(emailTooLong))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Order(8)
	void endTimeCannotBeInjected() throws Exception {
		// Trap 4: crafted extra fields are dropped, not applied.
		String crafted = BASE_JSON.formatted(FUTURE_DATE)
				.replace("\"10:00\"", "\"11:00\"")
				.replace("{", "{ \"endTime\": \"23:59\", \"status\": \"COMPLETED\", \"id\": 99999,");
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(crafted))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(99999)))
				.andExpect(jsonPath("$.endTime").value("11:55")); // computed, NOT 23:59
	}

	// ---------- AUTH PHASE 3: ownership ----------

	@Test
	@Order(9)
	void bookingWithAnotherAccountEmail_returns403() throws Exception {
		String body = BASE_JSON.formatted(FUTURE_DATE)
				.replace("\"10:00\"", "\"11:30\"")
				.replace(TEST_EMAIL, "someoneelse@example.com");
		mockMvc.perform(post("/api/bookings").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	@Order(10)
	void otherUsersBooking_isNotReadableOrMutable() throws Exception {
		// Create a SECOND account through the API (cleaned up in @AfterAll).
		String otherEmail = "slotmath-other@example.com";
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "name": "Other Account",
						  "email": "%s",
						  "password": "OtherPass123"
						}
						""").formatted(otherEmail)))
				.andExpect(result -> {
					int s = result.getResponse().getStatus();
					org.assertj.core.api.Assertions.assertThat(s).isIn(201, 409);
				});
		String otherToken = "Bearer " + mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(("""
						{
						  "email": "%s",
						  "password": "OtherPass123"
						}
						""").formatted(otherEmail)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
				.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		Long myBookingId = findBookingIdAt("10:00"); // belongs to TEST_EMAIL

		// The other account can neither read nor mutate our booking...
		mockMvc.perform(get("/api/bookings/" + myBookingId).header("Authorization", otherToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/bookings/" + myBookingId + "/cancel").header("Authorization", otherToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/bookings/" + myBookingId + "/reschedule").header("Authorization", otherToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\": \"" + FUTURE_DATE + "\", \"time\": \"15:00\"}"))
				.andExpect(status().isForbidden());
		// ...nor enumerate it by email.
		mockMvc.perform(get("/api/bookings").param("email", TEST_EMAIL).header("Authorization", otherToken))
				.andExpect(status().isForbidden());

		// The OWNER still has full access (sanity check).
		mockMvc.perform(get("/api/bookings/" + myBookingId).header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(myBookingId));
	}

	// ---------- lifecycle: reschedule / cancel ----------

	@Test
	@Order(11)
	void reschedule_movesBooking_andFreesOldSlot() throws Exception {
		Long id = findBookingIdAt("11:00");

		mockMvc.perform(put("/api/bookings/" + id + "/reschedule").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\": \"" + FUTURE_DATE + "\", \"time\": \"14:00\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.time").value("14:00"))
				.andExpect(jsonPath("$.endTime").value("14:55"))
				.andExpect(jsonPath("$.date").value(FUTURE_DATE.toString()));

		// 11:00 must be free again, 14:00 must be taken.
		MvcResult result = mockMvc
				.perform(get("/api/availability").param("serviceId", "1").param("stylistId", "1")
						.param("date", FUTURE_DATE.toString()))
				.andExpect(status().isOk()).andReturn();
		JsonNode slots = objectMapper.readTree(result.getResponse().getContentAsString()).path("slots");
		assertThatSlotsContain(slots, "11:00");
		assertThatSlotsDoNotContain(slots, "14:00");
	}

	@Test
	@Order(12)
	void rescheduleOntoTakenSlot_returns409() throws Exception {
		Long id = findBookingIdAt("14:00");
		// 10:00 is still CONFIRMED → moving onto it must conflict.
		mockMvc.perform(put("/api/bookings/" + id + "/reschedule").header("Authorization", bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\": \"" + FUTURE_DATE + "\", \"time\": \"10:00\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	@Order(13)
	void cancel_isIdempotent_andFreesSlot() throws Exception {
		Long id = findBookingIdAt("14:00");

		mockMvc.perform(put("/api/bookings/" + id + "/cancel").header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		// Second cancel: still 200, not 409 (idempotent by design).
		mockMvc.perform(put("/api/bookings/" + id + "/cancel").header("Authorization", bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		MvcResult result = mockMvc
				.perform(get("/api/availability").param("serviceId", "1").param("stylistId", "1")
						.param("date", FUTURE_DATE.toString()))
				.andExpect(status().isOk()).andReturn();
		JsonNode slots = objectMapper.readTree(result.getResponse().getContentAsString()).path("slots");
		assertThatSlotsContain(slots, "14:00");
	}

	// ---------- "my appointments" ----------

	@Test
	@Order(14)
	void listBookingsByEmail_returnsOnlyThisEmail() throws Exception {
		MvcResult result = mockMvc
				.perform(get("/api/bookings").param("email", TEST_EMAIL).header("Authorization", bearer()))
				.andExpect(status().isOk()).andReturn();
		JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
		org.assertj.core.api.Assertions.assertThat(list).allSatisfy(
				b -> org.assertj.core.api.Assertions.assertThat(b.path("email").asString()).isEqualTo(TEST_EMAIL));
	}

	// ---------- helpers ----------

	private Long findBookingIdAt(String time) throws Exception {
		MvcResult result = mockMvc
				.perform(get("/api/bookings").param("email", TEST_EMAIL).header("Authorization", bearer()))
				.andExpect(status().isOk()).andReturn();
		JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
		for (JsonNode b : list) {
			if (FUTURE_DATE.toString().equals(b.path("date").asString())
					&& time.equals(b.path("time").asString())
					&& "CONFIRMED".equals(b.path("status").asString())) {
				return b.path("id").asLong();
			}
		}
		throw new IllegalStateException("No confirmed test booking at " + time + " — an earlier test failed?");
	}

	private void assertThatSlotsDoNotContain(JsonNode slots, String time) {
		for (JsonNode s : slots) {
			org.assertj.core.api.Assertions.assertThat(s.asString()).isNotEqualTo(time);
		}
	}

	private void assertThatSlotsContain(JsonNode slots, String time) {
		java.util.List<String> list = new java.util.ArrayList<>();
		slots.forEach(s -> list.add(s.asString()));
		org.assertj.core.api.Assertions.assertThat(list).contains(time);
	}

	// ---------- cleanup ----------

	@AfterAll
	static void cleanupTestRows(@Autowired BookingRepository bookingRepository,
			@Autowired UserRepository userRepository,
			@Autowired EntityManager entityManager) {
		// Every row this class created carries the test email(s) — remove them
		// so re-runs start clean (the availability assertions assume an empty
		// day) and no test account/user data remains in the database.
		entityManager.clear();
		var emails = java.util.List.of(TEST_EMAIL, "slotmath-other@example.com");
		for (String email : emails) {
			var rows = bookingRepository.findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(email);
			bookingRepository.deleteAll(rows);
			var account = userRepository.findByEmailIgnoreCase(email).orElse(null);
			if (account != null) {
				userRepository.delete(account);
			}
		}
		bookingRepository.flush();
		userRepository.flush();
		System.out.println("[BookingApiIntegrationTest] cleaned up test bookings + accounts for " + emails);
	}
}
