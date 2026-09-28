/* ==========================================================================
   DEV-ONLY SAMPLE DATA — NOT PRODUCTION CODE · REMOVE AT INTEGRATION PHASE
   --------------------------------------------------------------------------
   Placeholder for the real backend. Every other frontend file must read
   data from HERE (or, later, from the API) — never hardcode values.

   SHAPE CONTRACT: this array mirrors the future `GET /api/services` response
   exactly (backend ServiceDto):
       [ { id, name, description, category, durationMinutes, bufferMinutes, price } ]

   Values match backend data.sql seed rows 1:1 (ids preserved). Currency: ₹.
   The SERVICES PAGE shows the full 8-service menu grouped by category (user
   decision); the HOME PAGE keeps its curated 4-service section untouched.
   When the real API connects, the backend becomes the single source of truth.
   ========================================================================== */

window.SAMPLE_SERVICES = [
	{
		id: 1,
		name: "Classic Haircut",
		description: "Tailored cut, wash and finish — for any hair type.",
		category: "Haircut",
		durationMinutes: 45,
		bufferMinutes: 10,
		price: 499.00
	},
	{
		id: 3,
		name: "Beard Trim & Shape",
		description: "Shape-up, outline and hot-towel finish.",
		category: "Beard",
		durationMinutes: 20,
		bufferMinutes: 5,
		price: 299.00
	},
	{
		id: 4,
		name: "Wash & Blow Dry",
		description: "Shampoo, conditioner and a smooth blow-dry finish.",
		category: "Styling",
		durationMinutes: 30,
		bufferMinutes: 5,
		price: 399.00
	},
	{
		id: 5,
		name: "Full Colour",
		description: "All-over colour with gloss. Patch test advised 48h before.",
		category: "Colour",
		durationMinutes: 90,
		bufferMinutes: 20,
		price: 1499.00
	},
	{
		id: 2,
		name: "Skin Fade",
		description: "Precision fade blended to the skin, styled to finish.",
		category: "Haircut",
		durationMinutes: 45,
		bufferMinutes: 10,
		price: 649.00
	},
	{
		id: 6,
		name: "Roots Touch-Up",
		description: "Regrowth coverage only — quick refresh between colours.",
		category: "Colour",
		durationMinutes: 60,
		bufferMinutes: 15,
		price: 899.00
	},
	{
		id: 7,
		name: "Hair Treatment",
		description: "Deep-repair mask, scalp massage and steam.",
		category: "Treatment",
		durationMinutes: 30,
		bufferMinutes: 10,
		price: 549.00
	},
	{
		id: 8,
		name: "Kids Haircut",
		description: "Gentle cut for ages 3–12, grown-ups welcome to stay.",
		category: "Haircut",
		durationMinutes: 30,
		bufferMinutes: 5,
		price: 349.00
	}
];

/* --------------------------------------------------------------------------
   STYLISTS — mirrors the future `GET /api/stylists` response (StylistDto):
       [ { id, name, role, bio, avatarUrl } ]
   Values match backend data.sql stylists seed rows 1:1. avatarUrl stays null
   until real photos exist — the UI falls back to monograms (no fake photos).
   -------------------------------------------------------------------------- */

window.SAMPLE_STYLISTS = [
	{
		id: 1,
		name: "Maya Chen",
		role: "Senior Stylist",
		bio: "12 years behind the chair. Precision cuts and calm energy — you are in good hands.",
		avatarUrl: null
	},
	{
		id: 2,
		name: "Omar Haddad",
		role: "Barber & Stylist",
		bio: "Fades, beards and sharp lines. Talks football, never pushes small talk.",
		avatarUrl: null
	},
	{
		id: 3,
		name: "Sofia Reyes",
		role: "Colour Specialist",
		bio: "Balayage, vivids and gentle blonde. Will always tell you what your hair can take.",
		avatarUrl: null
	}
];

/* --------------------------------------------------------------------------
   STYLIST↔SERVICE JUNCTION — mirrors the backend stylists_services table:
       [ { stylistId, serviceId } ]
   Values match backend data.sql junction seed rows 1:1. Used to derive each
   stylist's specialty chips (via the service categories below).
   -------------------------------------------------------------------------- */

window.SAMPLE_STYLIST_SERVICES = [
	// Maya: cuts + colour + treatments + blow dry + kids
	{ stylistId: 1, serviceId: 1 },
	{ stylistId: 1, serviceId: 4 },
	{ stylistId: 1, serviceId: 5 },
	{ stylistId: 1, serviceId: 6 },
	{ stylistId: 1, serviceId: 7 },
	{ stylistId: 1, serviceId: 8 },
	// Omar: cuts + fade + beard + blow dry + kids
	{ stylistId: 2, serviceId: 1 },
	{ stylistId: 2, serviceId: 2 },
	{ stylistId: 2, serviceId: 3 },
	{ stylistId: 2, serviceId: 4 },
	{ stylistId: 2, serviceId: 8 },
	// Sofia: colour + treatments + blow dry
	{ stylistId: 3, serviceId: 4 },
	{ stylistId: 3, serviceId: 5 },
	{ stylistId: 3, serviceId: 6 },
	{ stylistId: 3, serviceId: 7 }
];

/* Full menu ordering for the services PAGE (category-grouped layout).
/* These are all the categories that exist in the 8-service seed data. */

window.SAMPLE_SERVICE_GROUPS = [
	{ category: "Haircut", title: "Haircuts", blurb: "Sharp, tailored cuts for every hair type and length." },
	{ category: "Beard", title: "Beard", blurb: "Crisp lines and clean shape-ups, hot-towel finish." },
	{ category: "Colour", title: "Colour", blurb: "From all-over gloss to quick regrowth touch-ups." },
	{ category: "Styling", title: "Styling", blurb: "Wash, blow-dry and polish for any occasion." },
	{ category: "Treatment", title: "Treatments", blurb: "Deep care that brings hair back to life." }
];

/* Full seed-service categories (all 8 data.sql rows) — dev-only lookup used
   to derive specialty chip labels for junction rows whose services are not
   in the curated 4 shown in the Services section. */

window.SAMPLE_SERVICE_CATEGORIES = {
	1: "Haircut",
	2: "Haircut",
	3: "Beard",
	4: "Styling",
	5: "Colour",
	6: "Colour",
	7: "Treatment",
	8: "Haircut"
};

/* --------------------------------------------------------------------------
   HOME-PAGE CURATION (user decision): the home section shows ONLY these
   4 services — the full 8-service menu lives on the services page.
   -------------------------------------------------------------------------- */

window.SAMPLE_HOME_SERVICE_IDS = [1, 3, 4, 5];

/* --------------------------------------------------------------------------
   REVIEWS — DEV-ONLY SAMPLE/PREVIEW CONTENT · REMOVE AT INTEGRATION PHASE
   --------------------------------------------------------------------------
   The database currently has NO review seed rows, so these six entries are
   INVENTED PREVIEW TEXT used only to develop and test the reviews UI.
   They are presented on the site as a preview — never as real testimonials.

   SHAPE CONTRACT: mirrors the future `GET /api/reviews` response
   (ReviewEntity + joined display fields the API will provide):
       [ { id, bookingId, rating (1-5), comment, guestName, serviceName, createdAt } ]
   When the real API connects, the list comes dynamically from the database —
   the frontend must not hardcode the count (6 is the current sample length).
   -------------------------------------------------------------------------- */

/* ==========================================================================
   PHASE 5 DEV-ONLY DATA — BOOKINGS + STYLIST HOURS + SLOT ENGINE
   --------------------------------------------------------------------------
   Mirrors the future API contracts 1:1 (integration phase swaps this module;
   nothing else changes):
     SAMPLE_STYLIST_HOURS  ~ working_hours table (weekday 1=Mon .. 7=Sun)
     SAMPLE_BOOKINGS       ~ GET /api/bookings?email= response shape
     SAMPLE_SLOTS.slotsFor ~ GET /api/availability?serviceId=&date=&stylistId=

   Booking times serialize as HH:mm (backend @JsonFormat). Status values match
   the DB enum: CONFIRMED / COMPLETED / CANCELLED / NO_SHOW.
   ALL OF THIS IS SAMPLE DATA — clearly marked, removed at integration.
   ========================================================================== */

window.SAMPLE_STYLIST_HOURS = [
	// Maya: Mon–Fri 09:00–17:00 (matches data.sql)
	{ stylistId: 1, weekdays: [1, 2, 3, 4, 5], start: "09:00", end: "17:00" },
	// Omar: Tue–Sat 10:00–18:00
	{ stylistId: 2, weekdays: [2, 3, 4, 5, 6], start: "10:00", end: "18:00" },
	// Sofia: Wed–Sun 09:30–17:30
	{ stylistId: 3, weekdays: [3, 4, 5, 6, 7], start: "09:30", end: "17:30" }
];

window.SAMPLE_BOOKINGS = [
	{
		id: 1001,
		customerName: "Priya S.",
		email: "priya@example.com",
		phone: "555-0111",
		serviceId: 5,
		stylistId: 3,
		date: "2026-10-02",
		time: "11:00",
		endTime: "12:30",
		status: "CONFIRMED",
		notes: "",
		createdAt: "2026-09-20"
	},
	{
		id: 1002,
		customerName: "Priya S.",
		email: "priya@example.com",
		phone: "555-0111",
		serviceId: 1,
		stylistId: 1,
		date: "2026-09-05",
		time: "10:00",
		endTime: "10:55",
		status: "COMPLETED",
		notes: "",
		createdAt: "2026-08-30"
	},
	{
		id: 1003,
		customerName: "Rahul M.",
		email: "rahul@example.com",
		phone: "555-0122",
		serviceId: 3,
		stylistId: 2,
		date: "2026-09-18",
		time: "16:00",
		endTime: "16:25",
		status: "CANCELLED",
		notes: "",
		createdAt: "2026-09-10"
	},
	{
		id: 1004,
		customerName: "Aisha K.",
		email: "aisha@example.com",
		phone: "555-0133",
		serviceId: 1,
		stylistId: 1,
		date: "2026-09-28",
		time: "10:00",
		endTime: "10:55",
		status: "CONFIRMED",
		notes: "",
		createdAt: "2026-09-21"
	}
];

/* --------------------------------------------------------------------------
   SLOT ENGINE (pure, dev-only) — mirrors the backend availability behavior:
   15-minute grid · slot must END the same day inside the shift ·
   slot occupies [start, start + duration + buffer) · CONFIRMED bookings
   block overlapping slots · past times filtered for today.
   -------------------------------------------------------------------------- */

window.SAMPLE_SLOTS = (function () {
	"use strict";

	function pad(n) { return (n < 10 ? "0" : "") + n; }

	function toMinutes(hhmm) {
		var p = hhmm.split(":");
		return Number(p[0]) * 60 + Number(p[1]);
	}

	function toHHMM(minutes) {
		return pad(Math.floor(minutes / 60)) + ":" + pad(minutes % 60);
	}

	/** Local-date ISO string (no UTC shifting). */
	function isoOf(date) {
		return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
	}

	function todayISO() {
		return isoOf(new Date());
	}

	/** ISO -> weekday 1=Mon .. 7=Sun (matches working_hours.weekday). */
	function weekdayOf(iso) {
		var p = iso.split("-");
		var d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
		return ((d.getDay() + 6) % 7) + 1;
	}

	function hoursFor(stylistId) {
		for (var i = 0; i < window.SAMPLE_STYLIST_HOURS.length; i++) {
			if (window.SAMPLE_STYLIST_HOURS[i].stylistId === stylistId) {
				return window.SAMPLE_STYLIST_HOURS[i];
			}
		}
		return null;
	}

	/** A date is bookable if today-or-future AND the stylist works that weekday. */
	function isBookableDate(stylistId, iso) {
		if (iso < todayISO()) {
			return false;
		}
		var h = hoursFor(stylistId);
		return !!(h && h.weekdays.indexOf(weekdayOf(iso)) !== -1);
	}

	/**
	 * Free slot start times ("HH:mm") for one stylist/date/service.
	 * excludeBookingId lets reschedule ignore the booking being moved.
	 */
	function slotsFor(stylistId, iso, service, excludeBookingId) {
		var h = hoursFor(stylistId);
		if (!h || !isBookableDate(stylistId, iso)) {
			return [];
		}
		var windowStart = toMinutes(h.start);
		var windowEnd = toMinutes(h.end);
		var span = service.durationMinutes + service.bufferMinutes;
		var bookings = (window.SAMPLE_BOOKINGS || []).filter(function (b) {
			return b.stylistId === stylistId && b.date === iso &&
				b.status === "CONFIRMED" && b.id !== excludeBookingId;
			});
		var out = [];
		for (var t = windowStart; t + span <= windowEnd; t += 15) {
			var slotEnd = t + span;
			var clash = bookings.some(function (b) {
				var bStart = toMinutes(b.time);
				var bEnd = toMinutes(b.endTime);
				return t < bEnd && slotEnd > bStart; // overlap
			});
			if (clash) {
				continue;
			}
			if (iso === todayISO() && t <= new Date().getHours() * 60 + new Date().getMinutes()) {
				continue; // honest: no stale/past slots
			}
			out.push(toHHMM(t));
		}
		return out;
	}

	return {
		toMinutes: toMinutes,
		toHHMM: toHHMM,
		isoOf: isoOf,
		todayISO: todayISO,
		weekdayOf: weekdayOf,
		hoursFor: hoursFor,
		isBookableDate: isBookableDate,
		slotsFor: slotsFor
	};
})();

/* Lookup helpers shared by booking + visits pages (mirrors GET /{id}). */

window.sampleServiceById = function (id) {
	for (var i = 0; i < (window.SAMPLE_SERVICES || []).length; i++) {
		if (window.SAMPLE_SERVICES[i].id === id) {
			return window.SAMPLE_SERVICES[i];
		}
	}
	return null;
};

window.sampleStylistById = function (id) {
	for (var i = 0; i < (window.SAMPLE_STYLISTS || []).length; i++) {
		if (window.SAMPLE_STYLISTS[i].id === id) {
			return window.SAMPLE_STYLISTS[i];
		}
	}
	return null;
};

window.SAMPLE_REVIEWS = [
	{
		id: 1,
		bookingId: 2001,
		rating: 5,
		comment: "They talked me through the shade before starting and the colour feels healthy — not fried.",
		guestName: "Priya S.",
		serviceName: "Full Colour",
		createdAt: "2026-09-14"
	},
	{
		id: 2,
		bookingId: 2002,
		rating: 5,
		comment: "Quick, precise and no fuss. The wash at the end is a very nice touch.",
		guestName: "Rahul M.",
		serviceName: "Classic Haircut",
		createdAt: "2026-09-12"
	},
	{
		id: 3,
		bookingId: 2003,
		rating: 4,
		comment: "Relaxed place — sat down, good conversation level, walked out feeling polished.",
		guestName: "Aisha K.",
		serviceName: "Wash & Blow Dry",
		createdAt: "2026-09-10"
	},
	{
		id: 4,
		bookingId: 2004,
		rating: 5,
		comment: "Best shape-up I have had. They actually listen instead of rushing the clippers.",
		guestName: "Daniel V.",
		serviceName: "Beard Trim & Shape",
		createdAt: "2026-09-08"
	},
	{
		id: 5,
		bookingId: 2005,
		rating: 4,
		comment: "Gentle on my scalp and the gloss turned out exactly like the swatch we picked.",
		guestName: "Meera T.",
		serviceName: "Full Colour",
		createdAt: "2026-09-06"
	},
	{
		id: 6,
		bookingId: 2006,
		rating: 5,
		comment: "Clean space, calm crew, sharp cut. Booking again was a no-brainer.",
		guestName: "Arjun N.",
		serviceName: "Classic Haircut",
		createdAt: "2026-09-04"
	}
];
