package com.salon;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.salon.entity.BookingEntity;
import com.salon.entity.BookingStatus;
import com.salon.entity.UserEntity;
import com.salon.repository.BookingRepository;
import com.salon.repository.ReviewRepository;
import com.salon.repository.UserRepository;
import com.salon.service.ReviewService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/**
 * PHASE 4 — full-stack reviews tests: MockMvc → controller → services →
 * JPA → the real local SQL Server (isolated `SalonBooking_Test` when the
 * documented override is passed; same fixtures either way).
 *
 * Fixtures (data.sql): stylist 1 = Maya, service 1 = Classic Haircut.
 * Two accounts are created through the signup API, then FIVE bookings are
 * inserted directly through the repository (the API cannot create past or
 * COMPLETED bookings):
 *   A past-CONFIRMED (owner)      → eligible via the time-based hybrid rule
 *   B past-CANCELLED (owner)      → rejected 409
 *   C FUTURE-but-COMPLETED (owner) → eligible via the manual hybrid rule
 *   D future-CONFIRMED (owner)    → rejected 409 (not finished yet)
 *   E past-CONFIRMED (other user) → rejected 403 (ownership)
 *
 * Self-cleaning: accounts + bookings (+ reviews via the FK CASCADE) are
 * removed in @AfterAll; a self-heal at setup clears residue from an aborted
 * earlier run. No real customer row is touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // real commits, like production
class ReviewApiIntegrationTest {

	private static final String OWNER_EMAIL = "review-owner@example.com";
	private static final String OTHER_EMAIL = "review-other@example.com";
	private static final String PASSWORD = "ReviewPass1";

	private static final LocalDate PAST_CONFIRMED_DATE = LocalDate.now().minusDays(2);
	private static final LocalDate PAST_CANCELLED_DATE = LocalDate.now().minusDays(4);
	private static final LocalDate OTHER_PAST_DATE = LocalDate.now().minusDays(6);
	private static final LocalDate FUTURE_HYBRID_DATE = LocalDate.now().plusDays(30);
	private static final LocalDate FUTURE_ACTIVE_DATE = LocalDate.now().plusDays(31);

	private static Long pastConfirmedId;
	private static Long cancelledId;
	private static Long hybridCompletedId;
	private static Long futureActiveId;
	private static Long otherBookingId;

	private static String ownerToken;
	private static String otherToken;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ObjectMapper objectMapper;

	// ---------- fixtures ----------

	/** Accounts FIRST (so the bookings post-date them and the Phase-5A gate
	 *  lets the unverified owner see them), then the five bookings. */
	private synchronized void setupFixtures() throws Exception {
		if (pastConfirmedId != null) {
			return;
		}
		entityManager.clear();
		selfHeal();

		ownerToken = accountToken("Review Owner", OWNER_EMAIL);
		otherToken = accountToken("Other Guest", OTHER_EMAIL);

		pastConfirmedId = insertBooking(OWNER_EMAIL, "Priya Sharma",
				PAST_CONFIRMED_DATE, LocalTime.of(10, 0), BookingStatus.CONFIRMED);
		cancelledId = insertBooking(OWNER_EMAIL, "Priya Sharma",
				PAST_CANCELLED_DATE, LocalTime.of(11, 0), BookingStatus.CANCELLED);
		hybridCompletedId = insertBooking(OWNER_EMAIL, "Priya Sharma",
				FUTURE_HYBRID_DATE, LocalTime.of(10, 0), BookingStatus.COMPLETED);
		futureActiveId = insertBooking(OWNER_EMAIL, "Priya Sharma",
				FUTURE_ACTIVE_DATE, LocalTime.of(10, 0), BookingStatus.CONFIRMED);
		otherBookingId = insertBooking(OTHER_EMAIL, "Asha Verma",
				OTHER_PAST_DATE, LocalTime.of(10, 0), BookingStatus.CONFIRMED);
	}

	private void selfHeal() {
		deleteEverythingFor(OWNER_EMAIL);
		deleteEverythingFor(OTHER_EMAIL);
	}

	private void deleteEverythingFor(String email) {
		// reviews go with the bookings via the FK ON DELETE CASCADE
		bookingRepository.flush();
		var bookings = bookingRepository
				.findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(email);
		if (!bookings.isEmpty()) {
			bookingRepository.deleteAll(bookings);
			bookingRepository.flush();
		}
		UserEntity account = userRepository.findByEmailIgnoreCase(email).orElse(null);
		if (account != null) {
			userRepository.delete(account);
			userRepository.flush();
		}
	}

	private String accountToken(String name, String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"" + name + "\", \"email\": \"" + email
								+ "\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isCreated());
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("token").asString();
	}

	private Long insertBooking(String email, String customerName, LocalDate date,
			LocalTime time, BookingStatus status) {
		BookingEntity b = new BookingEntity();
		b.setServiceId(1L);
		b.setStylistId(1L);
		b.setBookingDate(date);
		b.setBookingTime(time);
		b.setEndTime(time.plusMinutes(55)); // Classic Haircut: 45 + 10 buffer
		b.setStatus(status);
		b.setCustomerName(customerName);
		b.setPhone("0700000000");
		b.setEmail(email);
		b.setCreatedAt(LocalDateTime.now());
		return bookingRepository.saveAndFlush(b).getId();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private String reviewJson(Long bookingId, int rating, String comment) {
		return "{\"bookingId\": " + bookingId + ", \"rating\": " + rating
				+ (comment == null ? "" : ", \"comment\": \"" + comment + "\"") + "}";
	}

	// ---------- tests (ordered) ----------

	@Test
	@Order(1)
	void getReviews_isPublic_returnsArray() throws Exception {
		setupFixtures(); // also self-heals any residue from an aborted run
		mockMvc.perform(get("/api/reviews")) // no token at all
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());
	}

	@Test
	@Order(2)
	void postWithoutToken_returns401() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 5, "No token here.")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@Order(3)
	void postOnFutureActiveBooking_returns409() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(futureActiveId, 5, "Too early!")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	@Order(4)
	void postOnCancelledBooking_returns409() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(cancelledId, 5, "Cancelled visit.")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	@Order(5)
	void postOnSomeoneElsesBooking_returns403_genericMessage() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(otherBookingId, 5, "Not mine.")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("You can only review your own bookings."));
	}

	@Test
	@Order(6)
	void postOnUnknownBooking_returns404_generic() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(999999L, 5, "Ghost booking.")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not found."));
	}

	@Test
	@Order(7)
	void postValidation_ratingAndComment_outOfRangeOrTooLong_returns400WithFields() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 0, "Zero stars.")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.rating").exists());
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 6, "Six stars?")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.rating").exists());
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 5, "x".repeat(1001))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.comment").exists());
	}

	@Test
	@Order(8)
	void postOnOwnPastConfirmedBooking_returns201_maskedNameAndJoinedNames() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 5, "Precise, warm, and on time.")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookingId").value(pastConfirmedId.intValue()))
				.andExpect(jsonPath("$.rating").value(5))
				.andExpect(jsonPath("$.comment").value("Precise, warm, and on time."))
				.andExpect(jsonPath("$.guestName").value("Priya S.")) // full name never published
				.andExpect(jsonPath("$.serviceName").value("Classic Haircut"))
				.andExpect(jsonPath("$.stylistName").value("Maya Chen")) // data.sql truth
				.andExpect(jsonPath("$.createdAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}")));
	}

	@Test
	@Order(9)
	void duplicateReview_returns409() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 4, "Trying twice.")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	@Order(10)
	void hybrid_manualCompletedFutureBooking_isEligible_returns201() throws Exception {
		setupFixtures();
		// booking C sits in the FUTURE but the salon marked it COMPLETED
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(ownerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(hybridCompletedId, 4, "Marked complete by the salon.")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookingId").value(hybridCompletedId.intValue()))
				.andExpect(jsonPath("$.rating").value(4));
	}

	@Test
	@Order(11)
	void crossAccount_otherUserReviewsOwnersBooking_returns403() throws Exception {
		setupFixtures();
		mockMvc.perform(post("/api/reviews").header("Authorization", bearer(otherToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reviewJson(pastConfirmedId, 5, "Poaching a review slot.")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("You can only review your own bookings."));
	}

	@Test
	@Order(12)
	void getReviews_returnsExactlyTheTwoCreatedReviews() throws Exception {
		setupFixtures();
		mockMvc.perform(get("/api/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].bookingId",
						containsInAnyOrder(pastConfirmedId.intValue(), hybridCompletedId.intValue())))
				.andExpect(jsonPath("$[*].guestName", containsInAnyOrder("Priya S.", "Priya S.")));
	}

	// ---------- cleanup ----------

	/** Reviews vanish with their bookings (FK ON DELETE CASCADE); accounts
	 *  are removed explicitly. Nothing else is touched. */
	@AfterAll
	static void cleanup(@Autowired BookingRepository bookingRepository,
			@Autowired UserRepository userRepository,
			@Autowired ReviewRepository reviewRepository,
			@Autowired EntityManager entityManager) {
		entityManager.clear();
		for (String email : new String[] { OWNER_EMAIL, OTHER_EMAIL }) {
			bookingRepository.deleteAll(bookingRepository
					.findByEmailIgnoreCaseOrderByBookingDateDescBookingTimeDesc(email));
			UserEntity account = userRepository.findByEmailIgnoreCase(email).orElse(null);
			if (account != null) {
				userRepository.delete(account);
			}
		}
		bookingRepository.flush();
		userRepository.flush();
		// defensive: zero reviews must remain for the fixture bookings
		reviewRepository.flush();
		System.out.println("[ReviewApiIntegrationTest] cleaned up reviews + bookings + accounts");
	}
}
