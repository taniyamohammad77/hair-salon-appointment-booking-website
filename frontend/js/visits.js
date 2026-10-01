/* ==========================================================================
   MY VISITS (integration step 5 + AUTH PHASE 4)
   --------------------------------------------------------------------------
   AUTH PHASE 4: identity = the logged-in account. The page no longer offers
   an arbitrary email lookup (the backend now 403s it anyway): the account
   email from /api/auth/me IS the lookup; not-logged-in visitors get an
   honest "log in" prompt instead of a form that cannot work. Real rows
   still come from GET /api/bookings?email=<account email> — now with the
   bearer token attached automatically by api.js — and cancel/reschedule
   carry the token too (backend enforces ownership).

   HONESTY RULES (user rule 5/10):
     - API unavailable → documented sample fallback (seed + session rows for
       the ACCOUNT email), announced with an honest banner + "(sample data)"
       result title. Sample bookings are NEVER presented as real appointments.
     - A failed CANCEL/RESCHEDULE of a REAL booking never flips the page to
       sample data: the last good real rows stay on screen with an honest
       message (a fallback here could HIDE the user's real booking).
     - The local cancel/reschedule mutations only ever run on SAMPLE rows;
       real rows are changed exclusively by the backend.
   ========================================================================== */
(function () {
	"use strict";

	var S = window.SAMPLE_SLOTS || {};
	var resultWrap = document.querySelector("[data-visits-result]");
	var emptyWrap = document.querySelector("[data-visits-empty]");
	var statusEl = document.querySelector("[data-visits-status]");
	if (!resultWrap || typeof window.SAMPLE_BOOKINGS === "undefined") {
		return;
	}

	var currentEmail = "";
	var dataSource = null; // "api" | "sample" | null (before first lookup)
	var liveRows = [];     // last successful REAL rows (stale-safe re-render)

	function esc(text) {
		var d = document.createElement("div");
		d.textContent = String(text);
		return d.innerHTML;
	}

	function announce(text) {
		if (statusEl) { statusEl.textContent = text; }
	}

	/* ---------- mode banner (honest sample notice) ---------- */
	function visitsNoteEl() {
		var el = document.querySelector("[data-visits-note]");
		if (!el) {
			el = document.createElement("p");
			el.className = "api-note";
			el.setAttribute("data-visits-note", "");
			el.setAttribute("role", "status");
			var err = document.querySelector("[data-lookup-error]");
			if (err && err.parentNode) {
				err.parentNode.insertBefore(el, err.nextSibling);
			}
		}
		return el;
	}
	function showNote(message) {
		visitsNoteEl().textContent = message;
	}
	function hideNote() {
		var el = document.querySelector("[data-visits-note]");
		if (el) { el.textContent = ""; }
	}

	/* ---------- bookings store access (sample fallback only) ----------
	   Seed sample bookings (in-memory) + bookings created this session on
	   booking.html (sessionStorage "amore-sample-bookings"). Used ONLY when
	   the API is unavailable — clearly labelled, never passed off as real. */
	function sessionBookings() {
		try {
			return JSON.parse(window.sessionStorage.getItem("amore-sample-bookings") || "[]");
		} catch (e) {
			return [];
		}
	}

	function saveSessionBookings(rows) {
		try {
			window.sessionStorage.setItem("amore-sample-bookings", JSON.stringify(rows));
		} catch (e) { /* storage unavailable — changes stay in-page only */ }
	}

	function sampleRowsFor(email) {
		return window.SAMPLE_BOOKINGS.concat(sessionBookings()).filter(function (b) {
			return b.email.toLowerCase() === email.toLowerCase();
		});
	}

	/* ---------- data source: real API first, honest sample fallback ----------
	   allowFallback=false is used when REFRESHING real rows (after a cancel/
	   reschedule): a transient failure then keeps the last good real rows on
	   screen instead of hiding them behind sample data. */
	function fetchRows(email, allowFallback) {
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.request) {
			return Promise.resolve({
				rows: allowFallback ? sampleRowsFor(email) : [],
				source: "sample",
				error: null
			});
		}
		return window.AMORE_API.request("GET", "/api/bookings?email=" + encodeURIComponent(email))
			.then(function (res) {
				if (res.ok && Array.isArray(res.data)) {
					liveRows = res.data;
					return { rows: res.data, source: "api", error: null };
				}
				return {
					rows: allowFallback ? sampleRowsFor(email) : liveRows,
					source: allowFallback ? "sample" : "api",
					error: (res.error && typeof res.error === "string") ? res.error : "The salon server could not load visits."
				};
			});
	}

	/* ---------- upcoming/past split (unchanged logic) ---------- */
	function isUpcoming(b) {
		if (b.status !== "CONFIRMED") { return false; }
		var now = S.todayISO();
		return b.date > now || (b.date === now && b.endTime > currentHHMM());
	}

	function currentHHMM() {
		var d = new Date();
		return S.toHHMM(d.getHours() * 60 + d.getMinutes());
	}

	function isPastOrDone(b) {
		return !isUpcoming(b);
	}

	/* ---------- rendering ---------- */
	function statusBadge(status) {
		var cls = "status-badge";
		if (status === "CONFIRMED") { cls += " is-confirmed"; }
		else if (status === "COMPLETED") { cls += " is-completed"; }
		else if (status === "NO_SHOW") { cls += " is-noshow"; }
		return '<span class="' + cls + '">' + esc(status) + "</span>";
	}

	function visitCard(b) {
		var service = window.sampleServiceById(b.serviceId);
		var stylist = window.sampleStylistById(b.stylistId);
		var card = document.createElement("article");
		card.className = "visit-card" + (b.status === "CANCELLED" || b.status === "NO_SHOW" ? " is-cancelled" : "");

		var actions = "";
		if (isUpcoming(b)) {
			actions =
				'<div class="visit-actions">' +
				'<button class="btn btn-ghost" type="button" data-reschedule="' + b.id + '">Reschedule</button>' +
				'<button class="btn btn-accent" type="button" data-cancel="' + b.id + '">Cancel</button>' +
				"</div>";
		}

		card.innerHTML =
			'<div class="visit-main">' +
			'<p class="visit-service">' + esc(service ? service.name : "Service #" + b.serviceId) + "</p>" +
			'<p class="visit-meta">with ' + esc(stylist ? stylist.name : "stylist #" + b.stylistId) +
			" · " + esc(b.date) + " · " + esc(b.time) + "–" + esc(b.endTime) + "</p>" +
			"</div>" +
			statusBadge(b.status) +
			actions +
			'<div class="confirm-row" data-confirm-row="' + b.id + '" hidden>' +
			'<p class="confirm-text">Cancel this visit?</p>' +
			'<button class="btn btn-accent" type="button" data-confirm-cancel="' + b.id + '">Yes, cancel it</button>' +
			'<button class="btn btn-ghost" type="button" data-keep="' + b.id + '">Keep it</button>' +
			"</div>" +
			'<div class="visit-reschedule" data-reschedule-row="' + b.id + '" hidden>' +
			'<label class="field-label" for="reschedule-date-' + b.id + '">New date</label>' +
			'<input type="date" id="reschedule-date-' + b.id + '" data-reschedule-date="' + b.id + '" />' +
			'<div class="slot-grid" data-reschedule-slots="' + b.id + '"></div>' +
			'<p class="field-error" data-reschedule-error="' + b.id + '" hidden></p>' +
			'<div class="visit-actions">' +
			'<button class="btn btn-accent" type="button" data-confirm-reschedule="' + b.id + '" hidden>Confirm new time</button>' +
			'<button class="btn btn-ghost" type="button" data-close-reschedule="' + b.id + '">Close</button>' +
			"</div>" +
			"</div>";

		return card;
	}

	function renderFromRows(list, source) {
		var upcoming = list.filter(isUpcoming)
			.sort(function (a, b2) { return (a.date + a.time).localeCompare(b2.date + b2.time); });
		var past = list.filter(isPastOrDone)
			.sort(function (a, b2) { return (b2.date + b2.time).localeCompare(a.date + a.time); });

		if (!list.length) {
			resultWrap.hidden = true;
			emptyWrap.hidden = false;
			announce("No visits found for " + currentEmail);
			return;
		}
		resultWrap.hidden = false;
		emptyWrap.hidden = true;

		// honesty: the result title names the data source when sample
		var title = resultWrap.querySelector("[data-result-title]");
		if (title) {
			title.textContent = source === "api" ? "Upcoming" : "Upcoming (sample data)";
		}

		var upWrap = resultWrap.querySelector("[data-upcoming-list]");
		var pastWrap = resultWrap.querySelector("[data-past-list]");
		upWrap.innerHTML = "";
		pastWrap.innerHTML = "";

		if (!upcoming.length) {
			upWrap.innerHTML = '<p class="booking-hint">No upcoming visits — book one!</p>';
		}
		upcoming.forEach(function (b) { upWrap.appendChild(visitCard(b)); });
		past.forEach(function (b) { pastWrap.appendChild(visitCard(b)); });

		announce(list.length + " visit" + (list.length === 1 ? "" : "s") + " found for " + currentEmail +
			(source === "api" ? " (live)." : " (sample data — not real appointments)."));
	}

	/* ---------- lookup (shared by submit + CTA prefill) ---------- */
	function performLookup(email) {
		currentEmail = email;
		resultWrap.hidden = true;
		emptyWrap.hidden = true;
		announce("Looking up visits for " + email + "…");

		fetchRows(email, true).then(function (outcome) {
			if (email !== currentEmail) { return; } // a newer lookup superseded this one
			dataSource = outcome.source;

			if (outcome.source === "api") {
				hideNote();
			} else if (outcome.error) {
				showNote("Live visit lookup unavailable right now (" + outcome.error +
					" Showing sample visits only — these are not real appointments.");
			} else {
				hideNote();
			}
			renderFromRows(outcome.rows, outcome.source);
		});
	}

	/* Re-fetch and re-render without sample fallback (post-mutation). */
	function refreshReal() {
		fetchRows(currentEmail, false).then(function (outcome) {
			renderFromRows(outcome.rows, "api");
			// stale-safe notice AFTER the render announce (fetchRows keeps the
			// last good REAL rows and reports the failure via outcome.error)
			if (outcome.error) {
				announce("Cannot reach the salon server — showing your visits as last loaded.");
			}
		});
	}

	/* ---------- cancel (inline confirm; real = PUT, sample = local) ---------- */
	resultWrap.addEventListener("click", function (e) {
		var btn = e.target.closest("button");
		if (!btn) { return; }

		if (btn.hasAttribute("data-cancel")) {
			var id1 = btn.getAttribute("data-cancel");
			resultWrap.querySelector('[data-confirm-row="' + id1 + '"]').hidden = false;
			btn.hidden = true;
			return;
		}
		if (btn.hasAttribute("data-keep")) {
			var id2 = btn.getAttribute("data-keep");
			resultWrap.querySelector('[data-confirm-row="' + id2 + '"]').hidden = true;
			resultWrap.querySelector('[data-cancel="' + id2 + '"]').hidden = false;
			return;
		}
		if (btn.hasAttribute("data-confirm-cancel")) {
			var id3 = Number(btn.getAttribute("data-confirm-cancel"));
			if (dataSource === "api") {
				cancelReal(id3, btn);
			} else {
				cancelSample(id3);
			}
			return;
		}
		if (btn.hasAttribute("data-reschedule")) {
			openReschedule(Number(btn.getAttribute("data-reschedule")));
			return;
		}
		if (btn.hasAttribute("data-close-reschedule")) {
			var id4 = btn.getAttribute("data-close-reschedule");
			resultWrap.querySelector('[data-reschedule-row="' + id4 + '"]').hidden = true;
			return;
		}
		if (btn.hasAttribute("data-confirm-reschedule")) {
			confirmReschedule(Number(btn.getAttribute("data-confirm-reschedule")));
		}
	});

	function cancelReal(id, btn) {
		var row = resultWrap.querySelector('[data-confirm-row="' + id + '"]');
		var yesBtn = row ? row.querySelector("[data-confirm-cancel]") : null;
		if (yesBtn) {
			yesBtn.disabled = true;
			yesBtn.setAttribute("aria-busy", "true");
		}
		if (btn) { btn.disabled = true; }

		window.AMORE_API.request("PUT", "/api/bookings/" + id + "/cancel")
			.then(function (res) {
				if (res.ok) {
					announce("Visit cancelled.");
					refreshReal(); // re-render from fresh real rows (row stays, status CANCELLED)
				} else if (res.status === 404) {
					announce("This booking no longer exists on the salon server.");
					refreshReal();
				} else if (res.status === 409) {
					announce(res.error || "This visit can no longer be cancelled.");
					refreshReal();
				} else {
					announce((res.error || "Cancelling failed") + " — the visit was not cancelled.");
					refreshReal(); // restores the confirm row (unchanged booking)
				}
			});
	}

	function cancelSample(id3) {
		var b = findBooking(id3);
		if (b && b.status === "CONFIRMED") {
			b.status = "CANCELLED";
			var seeded = window.SAMPLE_BOOKINGS.indexOf(b);
			if (seeded === -1) {
				var rows = sessionBookings();
				var idx = rows.findIndex(function (r) { return r.id === b.id; });
				if (idx !== -1) { rows[idx].status = "CANCELLED"; saveSessionBookings(rows); }
			}
		}
		announce("Sample visit cancelled.");
		renderFromRows(sampleRowsFor(currentEmail), "sample");
	}

	function findBooking(id) {
		if (dataSource === "api") {
			return liveRows.find(function (b) { return b.id === id; }) || null;
		}
		return sampleRowsFor(currentEmail).find(function (b) { return b.id === id; }) || null;
	}

	/* ---------- reschedule (inline date + slots) ---------- */
	var reschedulePick = {};
	/* availability response guard per booking id (rapid date changes) */
	var rescheduleSeq = {};

	function openReschedule(id) {
		var b = findBooking(id);
		if (!b) { return; }
		reschedulePick[id] = "";
		var row = resultWrap.querySelector('[data-reschedule-row="' + id + '"]');
		var dateInput = row.querySelector("[data-reschedule-date]");
		dateInput.min = S.todayISO();
		// Same horizon as the booking wizard (16d): appointments must END the
		// same calendar day, so far-future dates are guaranteed dead-ends.
		var max = new Date();
		max.setDate(max.getDate() + 16);
		dateInput.max = S.isoOf(max);
		row.hidden = false;
		var grid = row.querySelector('[data-reschedule-slots="' + id + '"]');
		grid.innerHTML = '<p class="booking-hint">Pick a date to see free times.</p>';
	}

	function rescheduleSlotsSkeleton(grid) {
		grid.innerHTML = "";
		var wrap = document.createElement("div");
		wrap.className = "slot-grid-loading";
		wrap.setAttribute("aria-hidden", "true");
		for (var i = 0; i < 6; i++) {
			var pill = document.createElement("span");
			pill.className = "slot-skeleton skeleton-line";
			wrap.appendChild(pill);
		}
		grid.appendChild(wrap);
	}

	function paintRescheduleSlots(grid, slots, id, sourceNote) {
		grid.innerHTML = "";
		if (sourceNote) {
			var note = document.createElement("p");
			note.className = "booking-hint";
			note.textContent = sourceNote;
			grid.appendChild(note);
		}
		if (!slots.length) {
			grid.innerHTML = '<p class="booking-hint">No free times that day — try another date.</p>';
			return;
		}
		slots.forEach(function (hhmm) {
			var btn = document.createElement("button");
			btn.type = "button";
			btn.className = "slot";
			btn.textContent = hhmm;
			btn.setAttribute("aria-pressed", "false");
			btn.addEventListener("click", function () {
				grid.querySelectorAll(".slot").forEach(function (s2) {
					s2.classList.remove("is-selected");
					s2.setAttribute("aria-pressed", "false");
				});
				btn.classList.add("is-selected");
				btn.setAttribute("aria-pressed", "true");
				reschedulePick[id] = hhmm;
				var confirmBtn2 = resultWrap.querySelector('[data-confirm-reschedule="' + id + '"]');
				if (confirmBtn2) { confirmBtn2.hidden = false; }
			});
			grid.appendChild(btn);
		});
	}

	resultWrap.addEventListener("change", function (e) {
		var input = e.target.closest("[data-reschedule-date]");
		if (!input) { return; }
		var id = Number(input.getAttribute("data-reschedule-date"));
		var b = findBooking(id);
		var row = resultWrap.querySelector('[data-reschedule-row="' + id + '"]');
		var grid = row.querySelector('[data-reschedule-slots="' + id + '"]');
		var err = row.querySelector('[data-reschedule-error="' + id + '"]');
		var confirmBtn2 = row.querySelector('[data-confirm-reschedule="' + id + '"]');
		err.hidden = true;
		reschedulePick[id] = "";
		confirmBtn2.hidden = true;

		if (!input.value) {
			grid.innerHTML = '<p class="booking-hint">Pick a date to see free times.</p>';
			return;
		}

		if (dataSource === "api") {
			// real availability (live engine — shifts, time off, bookings)
			var seq = (rescheduleSeq[id] = (rescheduleSeq[id] || 0) + 1);
			rescheduleSlotsSkeleton(grid);
			window.AMORE_API.request("GET", "/api/availability" +
					"?serviceId=" + encodeURIComponent(b.serviceId) +
					"&stylistId=" + encodeURIComponent(b.stylistId) +
					"&date=" + encodeURIComponent(input.value))
				.then(function (res) {
					if (seq !== rescheduleSeq[id]) { return; } // superseded
					if (res.ok && res.data && Array.isArray(res.data.slots)) {
						paintRescheduleSlots(grid, res.data.slots, id, "");
					} else {
						// transient failure → sample engine, honestly labelled
						var service = window.sampleServiceById(b.serviceId);
						var slots = S.slotsFor(b.stylistId, input.value, service, id);
						paintRescheduleSlots(grid, slots, id,
							"Live availability unavailable — showing estimated sample times.");
					}
				});
		} else {
			// sample engine (unchanged Phase 5 behavior)
			var service2 = window.sampleServiceById(b.serviceId);
			var slots2 = S.slotsFor(b.stylistId, input.value, service2, id); // exclude itself
			paintRescheduleSlots(grid, slots2, id, "");
		}
	});

	function confirmReschedule(id) {
		var b = findBooking(id);
		var row = resultWrap.querySelector('[data-reschedule-row="' + id + '"]');
		var err = row.querySelector('[data-reschedule-error="' + id + '"]');
		var newDate = row.querySelector("[data-reschedule-date]").value;
		if (!newDate) {
			err.textContent = "Pick a new date first.";
			err.hidden = false;
			return;
		}
		if (!reschedulePick[id]) {
			err.textContent = "Pick a new time.";
			err.hidden = false;
			return;
		}

		if (dataSource === "api") {
			rescheduleReal(id, b, newDate, reschedulePick[id], err);
		} else {
			rescheduleSample(id, b, newDate, err);
		}
	}

	function rescheduleReal(id, b, newDate, newTime, err) {
		var row = resultWrap.querySelector('[data-reschedule-row="' + id + '"]');
		var confirmBtn2 = row.querySelector('[data-confirm-reschedule="' + id + '"]');
		confirmBtn2.disabled = true;
		confirmBtn2.setAttribute("aria-busy", "true");

		window.AMORE_API.request("PUT", "/api/bookings/" + id + "/reschedule", {
			body: { date: newDate, time: newTime }
		}).then(function (res) {
			confirmBtn2.disabled = false;
			confirmBtn2.removeAttribute("aria-busy");
			if (res.ok) {
				announce("Visit moved to " + newDate + " at " + newTime + ".");
				refreshReal(); // backend recomputed endTime — show fresh rows
				return;
			}
			if (res.status === 409) {
				// slot taken meanwhile — honest message + fresh slots for re-pick
				err.textContent = "That time was just booked by someone else — pick another.";
				err.hidden = false;
				// re-open the slot grid fresh for the chosen date
				rescheduleSeq[id] = (rescheduleSeq[id] || 0) + 1;
				var grid = row.querySelector('[data-reschedule-slots="' + id + '"]');
				rescheduleSlotsSkeleton(grid);
				window.AMORE_API.request("GET", "/api/availability" +
						"?serviceId=" + encodeURIComponent(b.serviceId) +
						"&stylistId=" + encodeURIComponent(b.stylistId) +
						"&date=" + encodeURIComponent(newDate))
					.then(function (r2) {
						if (r2.ok && r2.data && Array.isArray(r2.data.slots)) {
							paintRescheduleSlots(grid, r2.data.slots, id, "");
						} else {
							paintRescheduleSlots(grid, [], id, "");
						}
					});
				return;
			}
			// 400 (past date / validation) or anything else — honest, no change
			err.textContent = res.error || "Rescheduling failed — the visit was not moved.";
			err.hidden = false;
		});
	}

	function rescheduleSample(id, b, newDate, err) {
		var service = window.sampleServiceById(b.serviceId);
		// final re-check (mirrors the backend re-check before save)
		var stillFree = S.slotsFor(b.stylistId, newDate, service, id).indexOf(reschedulePick[id]) !== -1;
		if (!stillFree) {
			err.textContent = "That time was just taken — pick another.";
			err.hidden = false;
			return;
		}
		b.date = newDate;
		b.time = reschedulePick[id];
		var mins = S.toMinutes(reschedulePick[id]) + service.durationMinutes;
		b.endTime = S.toHHMM(mins);
		var seededIdx = window.SAMPLE_BOOKINGS.indexOf(b);
		if (seededIdx === -1) {
			var rows2 = sessionBookings();
			var idx2 = rows2.findIndex(function (r) { return r.id === b.id; });
			if (idx2 !== -1) { rows2[idx2] = b; saveSessionBookings(rows2); }
		}
		announce("Sample visit moved to " + newDate + " at " + reschedulePick[id] + ".");
		renderFromRows(sampleRowsFor(currentEmail), "sample");
	}

	/* ======================================================================
	   AUTH PHASE 4 — identity bootstrap. The lookup form is replaced by the
	   account identity: logged-out → honest login prompt (no fake data,
	   the backend would 401 anyway); logged-in → hide the form, revalidate
	   via /me, then auto-lookup the ACCOUNT email. No arbitrary email can
	   ever be queried from this page.
	   ====================================================================== */
	var lookupForm = document.querySelector("[data-lookup-form]");
	var lookupCard = lookupForm ? lookupForm.closest(".booking-card") : null;

	function renderLoginPrompt() {
		if (!lookupCard) { return; }
		lookupForm.hidden = true;
		var hint = lookupCard.querySelector(".booking-hint");
		if (hint) {
			hint.textContent = "Log in to see your visits — visits belong to your account.\u00A0";
		}
		var link = document.createElement("a");
		link.className = "btn btn-accent";
		link.href = "login.html?next=" + encodeURIComponent("/my-visits.html");
		link.textContent = "Log in";
		lookupForm.parentNode.insertBefore(link, lookupForm.nextSibling);
	}

	function initIdentity() {
		if (!window.AMORE_API || !window.AMORE_API.isLoggedIn()) {
			renderLoginPrompt();
			return;
		}
		window.AMORE_API.me().then(function (res) {
			if (!(res.ok && res.data && res.data.email)) {
				// dead/expired token (api.js cleared it) or server down — honest prompt
				renderLoginPrompt();
				return;
			}
			currentEmail = res.data.email;
			if (lookupForm) { lookupForm.hidden = true; }
			var hint = lookupCard ? lookupCard.querySelector(".booking-hint") : null;
			if (hint) {
				hint.textContent = "Showing visits for " + res.data.email + " (your account).";
				// PHASE 5A: unverified accounts can't see bookings that existed
				// before they signed up — say so honestly (no redesign).
				if (res.data.verified === false) {
					hint.textContent += " Older visits (made before you signed up) appear once the salon verifies your email.";
				}
			}
			performLookup(res.data.email);
		});
	}

	initIdentity();
})();
