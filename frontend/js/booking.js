/* ==========================================================================
   BOOKING WIZARD (integration step 4)
   --------------------------------------------------------------------------
   Six steps: Service → Stylist → Date & time → Details → Review → Confirm.

   Data sources (user decisions — Step 3 = availability, Step 4 = submit):
     - STEP 3 SLOTS: LIVE via GET /api/availability (js/api.js — the one
       centralized fetch layer). The backend slot engine (shifts, time off,
       confirmed bookings, duration+buffer, past filter) is the single
       source of truth for free times. Sample engine (SAMPLE_SLOTS) is the
       documented fallback ONLY when the API is unavailable — announced
       honestly via [data-slots-note]; never claimed as live. Slots are
       cached per service+stylist+date and refetched when any of the three
       changes or on re-entry to step 3.
     - SUBMISSION (Step 4): LIVE via POST /api/bookings through the same
       AMORE_API layer. Success (201) → the REAL booking id is shown in a
       clear "Booking confirmed" state (no sample wording, no My Visits
       claim — My Visits is not integrated yet). HTTP 409 → the picked slot
       was just taken: the slot cache is invalidated, the wizard returns to
       step 3 with an honest message and FRESH availability. Any other
       failure (API down, 4xx/5xx) → honest error on the review step; NO
       sample booking is ever created or claimed (user rule 5/10) — the
       user can retry Confirm. The old SAMPLE submission path is kept only
       as defensive dead code (never reachable from the UI).
     - Service/stylist lists + compatibility: sample data (unchanged).

   Deep links: booking.html?service=<id> and/or ?stylist=<id> preselect.
   ========================================================================== */
(function () {
	"use strict";

	var S = window.SAMPLE_SLOTS || {};
	var services = window.SAMPLE_SERVICES || [];
	var stylists = window.SAMPLE_STYLISTS || [];

	var wizard = document.querySelector("[data-wizard]");
	if (!wizard || typeof window.SAMPLE_SERVICES === "undefined") {
		return; // not on the booking page, or sample module missing
	}

	var TOTAL_STEPS = 6;
	var state = {
		step: 1,
		serviceId: null,
		stylistId: null,
		date: "",
		time: "",
		name: "",
		email: "",
		phone: "",
		notes: ""
	};

	/* ---------- elements ---------- */
	var panels = {};
	for (var i = 1; i <= TOTAL_STEPS; i++) {
		panels[i] = wizard.querySelector('[data-panel="' + i + '"]');
	}
	var stepTabs = {};
	document.querySelectorAll("[data-step-tab]").forEach(function (tab) {
		stepTabs[Number(tab.getAttribute("data-step-tab"))] = tab;
	});
	var backBtn = wizard.querySelector("[data-back]");
	var nextBtn = wizard.querySelector("[data-next]");
	var confirmBtn = wizard.querySelector("[data-confirm]");
	var nav = wizard.querySelector("[data-wizard-nav]");

	function esc(text) {
		var d = document.createElement("div");
		d.textContent = String(text);
		return d.innerHTML;
	}

	function fieldError(input, errEl, message) {
		if (message) {
			if (input) input.setAttribute("aria-invalid", "true");
			errEl.textContent = message;
			errEl.hidden = false;
			return false;
		}
		if (input) input.removeAttribute("aria-invalid");
		errEl.hidden = true;
		return true;
	}

	function flashError(text) {
		var el = wizard.querySelector("[data-review-error]");
		el.textContent = text;
		el.hidden = false;
		el.setAttribute("role", "alert");
	}

	/* ======================================================================
	   STEP 1 — SERVICE
	   ====================================================================== */
	function renderServices() {
		var wrap = wizard.querySelector("[data-service-options]");
		wrap.innerHTML = "";
		services.forEach(function (s) {
			var btn = document.createElement("button");
			btn.type = "button";
			btn.className = "booking-option" + (state.serviceId === s.id ? " is-selected" : "");
			btn.setAttribute("data-service-id", s.id);
			btn.setAttribute("aria-pressed", state.serviceId === s.id ? "true" : "false");
			btn.innerHTML =
				'<span class="booking-option-title">' + esc(s.name) + "</span>" +
				'<span class="booking-option-meta">' + esc(s.durationMinutes) + " min · ₹" +
				Number(s.price).toLocaleString("en-IN", { maximumFractionDigits: 0 }) + "</span>" +
				'<span class="booking-option-bio">' + esc(s.description || "") + "</span>";
			btn.addEventListener("click", function () {
				if (state.serviceId !== s.id) {
					state.serviceId = s.id;
					state.time = ""; // new service → old slot may not fit the new duration
				}
				renderServices();
			});
			wrap.appendChild(btn);
		});
	}

	/* ======================================================================
	   STEP 2 — STYLIST (only those who offer the chosen service)
	   ====================================================================== */
	function compatibleStylists(serviceId) {
		return stylists.filter(function (st) {
			return (window.SAMPLE_STYLIST_SERVICES || []).some(function (row) {
				return row.stylistId === st.id && row.serviceId === serviceId;
			});
		});
	}

	function renderStylists() {
		var wrap = wizard.querySelector("[data-stylist-options]");
		var chosen = window.sampleServiceById(state.serviceId);
		wizard.querySelector("[data-chosen-service]").textContent = chosen ? chosen.name : "—";
		wrap.innerHTML = "";

		var list = compatibleStylists(state.serviceId);
		if (state.stylistId && !list.some(function (st) { return st.id === state.stylistId; })) {
			state.stylistId = null; // preselected stylist can't do this service
		}

		list.forEach(function (st) {
			var btn = document.createElement("button");
			btn.type = "button";
			btn.className = "booking-option" + (state.stylistId === st.id ? " is-selected" : "");
			btn.setAttribute("data-stylist-id", st.id);
			btn.setAttribute("aria-pressed", state.stylistId === st.id ? "true" : "false");
			btn.innerHTML =
				'<span class="booking-option-title">' + esc(st.name) + "</span>" +
				'<span class="booking-option-meta">' + esc(st.role || "") + "</span>" +
				'<span class="booking-option-bio">' + esc(st.bio || "") + "</span>";
			btn.addEventListener("click", function () {
				state.stylistId = st.id;
				state.time = ""; // new stylist → old slots invalid
				renderStylists();
			});
			wrap.appendChild(btn);
		});
	}

	/* ======================================================================
	   STEP 3 — DATE & TIME (LIVE availability, honest sample fallback)
	   ====================================================================== */

	/* Cache of the last availability fetch, keyed service|stylist|date.
	   Refetch happens whenever the key changes (or on re-entry), so the
	   grid never silently shows stale slots (rule 10). */
	var slotsCache = { key: null, slots: [], source: "sample", error: null };
	var slotsSeq = 0; // guard against out-of-order responses

	function slotsNoteEl() {
		var el = wizard.querySelector("[data-slots-note]");
		if (!el) {
			el = document.createElement("p");
			el.className = "api-note";
			el.setAttribute("data-slots-note", "");
			el.setAttribute("role", "status");
			var err = wizard.querySelector("[data-slot-error]");
			if (err && err.parentNode) {
				err.parentNode.insertBefore(el, err);
			}
		}
		return el;
	}

	function loadSlots(serviceId, stylistId, date) {
		var key = serviceId + "|" + stylistId + "|" + date;
		if (slotsCache.key === key) {
			// cache hit: no seq involved — the current global is this result's seq
			return Promise.resolve({ result: slotsCache, seq: slotsSeq });
		}
		var seq = ++slotsSeq;
		var useApi = typeof window.AMORE_API !== "undefined" && window.AMORE_API.request;
		var call = useApi
			? window.AMORE_API.request("GET", "/api/availability" +
				"?serviceId=" + encodeURIComponent(serviceId) +
				"&stylistId=" + encodeURIComponent(stylistId) +
				"&date=" + encodeURIComponent(date))
			: Promise.resolve({ ok: false, status: 0, data: null, error: null });

		return call.then(function (res) {
			if (seq !== slotsSeq) {
				return null; // a newer request superseded this one
			}
			if (res.ok && res.data && Array.isArray(res.data.slots)) {
				// success → cached: re-picks/re-entries of this key skip the refetch
				slotsCache = { key: key, slots: res.data.slots, source: "api", error: null };
				return { result: slotsCache, seq: seq };
			}
			// API unreachable/unhappy → documented sample fallback (honest).
			// NOT cached: the next selection/re-entry of this same key retries
			// the real API automatically — a failure must never stick.
			var service = window.sampleServiceById(serviceId);
			return {
				result: {
					key: null,
					slots: service ? S.slotsFor(stylistId, date, service) : [],
					source: "sample",
					error: (res.error && typeof res.error === "string") ? res.error : null
				},
				seq: seq
			};
		});
	}

	function renderSlots() {
		var grid = wizard.querySelector("[data-slot-grid]");
		var hint = wizard.querySelector("[data-slots-hint]");
		var err = wizard.querySelector("[data-slot-error]");
		err.hidden = true;
		grid.innerHTML = "";

		var service = window.sampleServiceById(state.serviceId);
		if (!state.date || !service || !state.stylistId) {
			hint.textContent = "Choose a date to see free times.";
			return;
		}

		// loading state: existing shimmer pills, honest hint (no fake slots)
		hint.textContent = "Checking free times…";
		var skeleton = document.createElement("div");
		skeleton.className = "slot-grid-loading";
		skeleton.setAttribute("aria-hidden", "true");
		for (var si = 0; si < 6; si++) {
			var pill = document.createElement("span");
			pill.className = "slot-skeleton skeleton-line";
			skeleton.appendChild(pill);
		}
		grid.appendChild(skeleton);

		loadSlots(state.serviceId, state.stylistId, state.date).then(function (outcome) {
			// supersede check uses the seq ISSUED WITH THIS REQUEST (captured
			// inside loadSlots) — a pre-call snapshot would always be stale,
			// because loadSlots increments the counter when it fires.
			if (!outcome || outcome.seq !== slotsSeq) {
				return; // superseded by a newer selection
			}
			var result = outcome.result;
			grid.innerHTML = "";

			var note = slotsNoteEl();
			if (result.source === "api") {
				note.textContent = "";
			} else if (result.error) {
				note.textContent = "Live availability unavailable right now (" + result.error +
					" Showing estimated sample times.";
			} else {
				note.textContent = "";
			}

			var slots = result.slots;
			if (state.time && slots.indexOf(state.time) === -1) {
				state.time = ""; // stale pick (service/stylist changed) — never keep it
			}
			if (!slots.length) {
				hint.textContent = "Fully booked — try another date.";
				return;
			}
			hint.textContent = slots.length + " free time" + (slots.length === 1 ? "" : "s") + " — pick one." +
				(result.source === "sample" ? " (sample times)" : "");

			slots.forEach(function (hhmm) {
				var btn = document.createElement("button");
				btn.type = "button";
				btn.className = "slot" + (state.time === hhmm ? " is-selected" : "");
				btn.textContent = hhmm;
				btn.setAttribute("aria-pressed", state.time === hhmm ? "true" : "false");
				btn.addEventListener("click", function () {
					state.time = hhmm;
					renderSlots(); // cache hit — no network, instant re-render
				});
				grid.appendChild(btn);
			});
		});
	}

	function initDateInput() {
		var input = wizard.querySelector("[data-date]");
		// SAMPLE_SLOTS.todayISO is the local-date helper (no API equivalent
		// needed — the backend enforces the real "not in the past" rule).
		input.min = S.todayISO();
		// Backend horizon: appointments must END the same calendar day, so a
		// late-Dec 31 date can never fit a full slot. 16 days keeps every
		// pickable date bookable for any seeded service (max 110 min incl.
		// buffer) — no date the user can choose is a guaranteed dead end.
		var max = new Date();
		max.setDate(max.getDate() + 16);
		input.max = S.isoOf(max);
		input.value = state.date;
		input.addEventListener("change", function () {
			state.date = input.value;
			state.time = "";
			renderSlots();
		});
	}

	/* ======================================================================
	   VALIDATION (mirrors backend rules: name ≤120, email ≤160 + format)
	   ====================================================================== */
	var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

	function validateDetails(show) {
		var nameIn = wizard.querySelector("[data-name]");
		var emailIn = wizard.querySelector("[data-email]");
		var ok = true;
		if (show) {
			ok = fieldError(nameIn, wizard.querySelector("[data-name-error]"),
				nameIn.value.trim().length >= 2 && nameIn.value.trim().length <= 120
					? "" : "Please enter your full name (2–120 characters).");
			ok = fieldError(emailIn, wizard.querySelector("[data-email-error]"),
				EMAIL_RE.test(emailIn.value.trim()) && emailIn.value.trim().length <= 160
					? "" : "Please enter a valid email (max 160 characters).") && ok;
		}
		state.name = nameIn.value.trim();
		state.email = emailIn.value.trim();
		state.phone = wizard.querySelector("[data-phone]").value.trim();
		state.notes = wizard.querySelector("[data-notes]").value.trim();
		return ok;
	}

	/* Live re-validate as the user types OR picks a browser autofill
	   suggestion (autofill fires input events; 'change' alone misses it). */
	["input", "change"].forEach(function (type) {
		wizard.addEventListener(type, function (e) {
			if (!e.target.matches("[data-name],[data-email]")) {
				return;
			}
			validateDetails(false); // update state silently, no error flash while typing
		});
	});

	function validateStep3(show) {
		var err = wizard.querySelector("[data-slot-error]");
		var ok = true;
		if (show && !state.date) {
			err.textContent = "Please pick a date.";
			err.hidden = false;
			ok = false;
		} else if (show && !state.time) {
			err.textContent = "Please pick a time.";
			err.hidden = false;
			ok = false;
		} else {
			err.hidden = true;
		}
		return ok;
	}

	/* ======================================================================
	   STEP 5 — REVIEW + STEP 6 — CONFIRMATION (real via POST /api/bookings)
	   ====================================================================== */
	function renderReview() {
		var s = window.sampleServiceById(state.serviceId);
		var st = window.sampleStylistById(state.stylistId);
		wizard.querySelector("[data-review-service]").textContent = s ? s.name : "—";
		wizard.querySelector("[data-review-stylist]").textContent = st ? st.name : "—";
		wizard.querySelector("[data-review-when]").textContent =
			state.date && state.time ? state.date + " at " + state.time : "—";
		wizard.querySelector("[data-review-name]").textContent = state.name || "—";
		wizard.querySelector("[data-review-email]").textContent = state.email || "—";
		wizard.querySelector("[data-review-phone]").textContent = state.phone || "—";
		wizard.querySelector("[data-review-notes]").textContent = state.notes || "—";
	}

	/* What the confirmation panel last rendered (real | sample). */
	var confirmation = null;

	/** Render panel 6 from a confirmation object:
	 *  { kind: "real", ref, summary }  — live booking, no sample wording,
	 *                                    no "View in My Visits" claim (My
	 *                                    Visits is not integrated yet).
	 *  { kind: "sample", ref, summary } — defensive-only, clearly labelled.
	 */
	function renderConfirmation(conf) {
		confirmation = conf;
		wizard.querySelector("[data-success-ref]").textContent = conf.ref;
		wizard.querySelector("[data-success-summary]").textContent = conf.summary;

		// honesty switches: the sample disclaimer stays sample-only. The
		// "View in My Visits" CTA is TRUE for both paths since Step 5 — real
		// bookings are discoverable via the real email lookup, and the CTA
		// pre-fills that email (approved Step-4 decision).
		var sampleNote = wizard.querySelector("[data-sample-note]");
		if (sampleNote) {
			sampleNote.hidden = conf.kind !== "sample";
		}
	}

	function buildRealConfirmation(data) {
		var s = window.sampleServiceById(state.serviceId);
		var st = window.sampleStylistById(state.stylistId);
		return {
			kind: "real",
			ref: String(data.id),
			// NO "Find it in My Visits" claim — My Visits is not integrated yet
			summary: (s ? s.name : "Service") + " with " + (st ? st.name : "stylist") +
				" — " + state.date + " at " + state.time +
				(data.endTime ? ", until " + data.endTime : "") + "."
		};
	}

	/** Defensive-only sample confirmation (kept from Phase 5; the submit
	 *  flow can no longer reach it — the real API is the only path). */
	function showSampleConfirmation() {
		var ref = "SAMPLE-" + Math.floor(100000 + Math.random() * 900000);
		var s = window.sampleServiceById(state.serviceId);
		var st = window.sampleStylistById(state.stylistId);
		renderConfirmation({
			kind: "sample",
			ref: ref,
			summary: (s ? s.name : "Service") + " with " + (st ? st.name : "stylist") +
				" — " + state.date + " at " + state.time + ". Find it in My Visits (email: " + state.email + ")."
		});

		// Dev-only persistence (session store) — unchanged Phase 5 behavior.
		var mins = S.toMinutes(state.time) + (s ? s.durationMinutes : 0);
		var booking = {
			id: 900000 + Math.floor(Math.random() * 99999),
			customerName: state.name,
			email: state.email,
			phone: state.phone,
			serviceId: state.serviceId,
			stylistId: state.stylistId,
			date: state.date,
			time: state.time,
			endTime: S.toHHMM(mins),
			status: "CONFIRMED",
			notes: state.notes,
			createdAt: S.todayISO()
		};
		try {
			var store = JSON.parse(window.sessionStorage.getItem("amore-sample-bookings") || "[]");
			store.push(booking);
			window.sessionStorage.setItem("amore-sample-bookings", JSON.stringify(store));
		} catch (e) { /* storage unavailable — sample booking stays in-page only */ }
	}

	/* ----------------------------------------------------------------------
	   SUBMISSION — real since Step 4: POST /api/bookings via AMORE_API.
	   201 → REAL "Booking confirmed" state with the backend's booking id.
	   409 → slot taken meanwhile: invalidate the slot cache, jump back to
	         step 3 with an honest message + fresh availability (recover).
	   anything else (down/4xx/5xx) → honest error on the review step; NO
	   sample booking is ever created or claimed (rule 5/10) — the user can
	   simply retry Confirm.
	   ---------------------------------------------------------------------- */
	function submitBooking() {
		var submitBtn = confirmBtn;
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.request) {
			// API layer missing is a setup bug, not a user situation — stay
			// honest either way: no sample booking, no fake confirmation.
			flashError("Booking is unavailable right now — the salon server could not be reached. Please try again.");
			return;
		}

		var s = window.sampleServiceById(state.serviceId);
		submitBtn.disabled = true;
		submitBtn.setAttribute("aria-busy", "true");

		// endTime is included ONLY to keep the in-session sample-store's
		// future cross-page story consistent if it ever reads this shape;
		// the backend always recomputes it from the DB service row (Trap 4)
		// and ignores client values.
		var payload = {
			serviceId: state.serviceId,
			stylistId: state.stylistId,
			date: state.date,
			time: state.time,
			customerName: state.name,
			email: state.email,
			phone: state.phone,
			notes: state.notes || null,
			endTime: s ? S.toHHMM(S.toMinutes(state.time) + s.durationMinutes) : null
		};

		window.AMORE_API.request("POST", "/api/bookings", { body: payload })
			.then(function (res) {
				if (res.ok && res.status === 201 && res.data && res.data.id) {
					wizard.querySelector("[data-review-error]").hidden = true;
					renderConfirmation(buildRealConfirmation(res.data));
					show(TOTAL_STEPS);
					return;
				}
				if (res.status === 409) {
					// slot taken meanwhile — recover: fresh availability on step 3.
					// show() FIRST, THEN flash: show() resets the alert region,
					// so flashing before it would hide the message instantly.
					slotsCache = { key: null, slots: [], source: "sample", error: null };
					show(3);
					flashError("That time was just booked by someone else. Please pick a new time — availability below is fresh.");
					return;
				}
				flashError(res.error || "Booking failed — please try again.");
			})
			.finally(function () {
				submitBtn.disabled = false;
				submitBtn.removeAttribute("aria-busy");
			});
	}

	/* ======================================================================
	   WIZARD NAVIGATION
	   ====================================================================== */
	function stepValid(step) {
		if (step === 1) {
			if (!state.serviceId) { flashError("Please pick a service first."); return false; }
		}
		if (step === 2) {
			if (!state.stylistId) { flashError("Please pick a stylist."); return false; }
		}
		if (step === 3 && !validateStep3(true)) { return false; }
		if (step === 4 && !validateDetails(true)) { return false; }
		return true;
	}

	function show(step) {
		state.step = step;
		for (var p = 1; p <= TOTAL_STEPS; p++) {
			panels[p].hidden = p !== step;
		}
		for (var t = 1; t <= TOTAL_STEPS; t++) {
			var tab = stepTabs[t];
			if (!tab) continue;
			tab.classList.toggle("is-active", t === step);
			tab.classList.toggle("is-done", t < step);
			tab.setAttribute("aria-current", t === step ? "step" : "false");
		}
		backBtn.hidden = step === 1 || step === TOTAL_STEPS;
		nextBtn.hidden = step >= TOTAL_STEPS - 1;
		confirmBtn.hidden = step !== TOTAL_STEPS - 1;
		nav.hidden = step === TOTAL_STEPS;
		wizard.querySelector("[data-review-error]").hidden = true;

		if (step === 2) { renderStylists(); }
		if (step === 3) { renderSlots(); }
		if (step === 5) { renderReview(); }
		// Panel 6 renders ONLY what was actually confirmed — show(6) is
		// reachable solely through submitBooking success (real) or the
		// defensive sample path, both of which set `confirmation` first.
		if (step === 6 && confirmation) { renderConfirmation(confirmation); }

		// move focus to the panel heading (a11y: SRs announce the step)
		var h = panels[step].querySelector("h2");
		if (h) {
			h.setAttribute("tabindex", "-1");
			h.focus({ preventScroll: true });
		}
		wizard.scrollIntoView({ block: "nearest" });
	}

	nextBtn.addEventListener("click", function () {
		if (!stepValid(state.step)) { return; }
		show(Math.min(state.step + 1, TOTAL_STEPS));
	});
	backBtn.addEventListener("click", function () {
		show(Math.max(state.step - 1, 1));
	});
	confirmBtn.addEventListener("click", function () {
		if (!validateStep3(true) || !validateDetails(true)) {
			flashError("Something is missing — please review the highlighted fields.");
			return;
		}
		submitBooking(); // real submission (Step 4) — panel 6 only on API success
	});

	// Final-state CTA — valid for BOTH confirmation kinds since Step 5:
	// My Visits looks up REAL bookings by email, and this pre-fills it.
	wizard.querySelector("[data-cta-visits]").addEventListener("click", function () {
		try { window.sessionStorage.setItem("amore-visits-email", state.email); } catch (e) {}
	});

	// Edit links on the review step
	wizard.querySelectorAll("[data-edit]").forEach(function (btn) {
		btn.addEventListener("click", function () {
			show(Number(btn.getAttribute("data-edit")));
		});
	});

	/* ======================================================================
	   DEEP-LINK PRESELECTION (?service= / ?stylist=)
	   ====================================================================== */
	function applyDeepLinks() {
		var params = new URLSearchParams(window.location.search);
		var svc = Number(params.get("service"));
		var sty = Number(params.get("stylist"));
		if (svc && services.some(function (s) { return s.id === svc; })) {
			state.serviceId = svc;
		}
		if (sty && stylists.some(function (s) { return s.id === sty; })) {
			if (!state.serviceId ||
					compatibleStylists(state.serviceId).some(function (s) { return s.id === sty; })) {
				state.stylistId = sty;
			} else {
				state.serviceId = null; // invalid combo — start clean at step 1
			}
		}
	}

	applyDeepLinks();
	renderServices();
	initDateInput();
	show(1);
})();
