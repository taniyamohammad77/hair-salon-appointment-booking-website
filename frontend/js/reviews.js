/* ==========================================================================
   REVIEWS SECTION (frontend-only) — HOMEPAGE ONLY
   --------------------------------------------------------------------------
   Renders 6 DEV-ONLY sample reviews (js/sample-data.js, SAMPLE_REVIEWS) as a
   premium, subtle auto-moving carousel:

     - automatic smooth slide, one card at a time, continuous loop
     - NO next/previous buttons, NO arrows, NO manual controls, NO pause UI
     - pause only on hover/focus (needed for keyboard readability, not a
       visible control)
     - `prefers-reduced-motion: reduce` -> NO automatic movement: a readable
       static state instead (CSS handles the visual, JS skips the timer)

   Shape contract: SAMPLE_REVIEWS mirrors the future GET /api/reviews.
   When the real API connects, render whatever length the API returns —
   the count must NOT be hardcoded to 6. No backend calls in this file.
   ========================================================================== */
(function () {
	"use strict";

	var track = document.querySelector("[data-reviews-track]");

	if (!track || typeof window.SAMPLE_REVIEWS === "undefined" || window.SAMPLE_REVIEWS.length === 0) {
		return; // section not on this page, or no data
	}

	var SLIDE_INTERVAL_MS = 4500;
	var TRANSITION_MS = 600;
	var reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

	/* ---------- helpers ---------- */

	function escapeHtml(text) {
		var div = document.createElement("div");
		div.textContent = String(text);
		return div.innerHTML;
	}

	function starsHtml(rating) {
		var out = "";
		for (var i = 1; i <= 5; i++) {
			out += '<span class="star' + (i <= rating ? " star-filled" : "") + '" aria-hidden="true">' +
				(i <= rating ? "\u2605" : "\u2606") + "</span>";
		}
		return out;
	}

	/* ---------- card rendering ---------- */

	function cardEl(review) {
		var card = document.createElement("article");
		card.className = "review-card";
		var titleId = "review-title-" + review.id;
		card.setAttribute("aria-labelledby", titleId);
		// Phase 6 fix: cards are focusable so keyboard users can actually enter
		// the reviews region — that is what makes the existing focusin-pause
		// reachable (previously the region had NO focusable element, so the
		// keyboard pause could never trigger).
		card.setAttribute("tabindex", "0");

		card.innerHTML =
			'<div class="review-stars" role="img" aria-label="Rated ' + review.rating +
			' out of 5">' + starsHtml(review.rating) + "</div>" +
			'<blockquote class="review-quote">"' + escapeHtml(review.comment) + '"</blockquote>' +
			'<footer class="review-meta">' +
			'<cite class="review-guest" id="' + titleId + '">' + escapeHtml(review.guestName) + "</cite>" +
			'<span class="review-service">' + escapeHtml(review.serviceName || "") + "</span>" +
			"</footer>";

		return card;
	}

	function render() {
		window.SAMPLE_REVIEWS.forEach(function (review) {
			track.appendChild(cardEl(review));
		});
	}

	/* ---------- auto-movement (reviews ONLY — never Services/Stylists) ----------
	   track is a flex row; translateX by one card per tick; clone the first
	   card at the end so the loop is seamless; jump without transition after
	   the clone is reached.                                                          */

	var index = 0;
	var timer = null;

	function cardWidth() {
		var card = track.children[0];
		if (!card) {
			return 0;
		}
		var gap = parseFloat(getComputedStyle(track).columnGap || getComputedStyle(track).gap) || 0;
		return card.getBoundingClientRect().width + gap;
	}

	function applyOffset(animate) {
		var visible = visibleCount();
		var limit = track.children.length - visible; // furthest real position
		var target = Math.min(index, limit);
		track.style.transition = animate && !reducedMotion.matches
			? "transform " + TRANSITION_MS + "ms cubic-bezier(0.22, 0.61, 0.36, 1)"
			: "none";
		track.style.transform = "translateX(" + (-target * cardWidth()) + "px)";
	}

	function visibleCount() {
		var w = window.innerWidth;
		if (w <= 720) {
			return 1;
		}
		if (w <= 1024) {
			return 2;
		}
		return 3;
	}

	function next() {
		index += 1;
		applyOffset(true);
		if (index >= track.children.length - visibleCount()) {
			// reached the cloned first card — snap back to the real start
			// (Phase 6 fix: was `>`, which parked one EXTRA 4.5s tick on the
			// visually-identical clone frame and broke the slide rhythm)
			window.setTimeout(function () {
				index = 0;
				applyOffset(false);
			}, TRANSITION_MS + 30);
		}
	}

	function start() {
		if (reducedMotion.matches || timer || track.children.length <= visibleCount()) {
			return; // reduced motion: static readable state; also nothing to scroll
		}
		timer = window.setInterval(next, SLIDE_INTERVAL_MS);
	}

	function stop() {
		if (timer) {
			window.clearInterval(timer);
			timer = null;
		}
	}

	/* ---------- init ---------- */

	render();

	if (reducedMotion.matches) {
		// Static, readable state — CSS also switches the layout for this case.
		track.classList.add("is-static");
	} else {
		// Seamless loop: clone the first visible set of cards to the end.
		var visible = visibleCount();
		for (var i = 0; i < visible; i++) {
			var clone = track.children[i].cloneNode(true);
			clone.setAttribute("aria-hidden", "true");
			// Phase 6 fixes: clones must not be keyboard-focusable (aria-hidden +
			// focusable = violation) and must not duplicate the cite's id.
			clone.setAttribute("tabindex", "-1");
			var idEl = clone.querySelector("[id]");
			if (idEl) {
				idEl.removeAttribute("id");
			}
			track.appendChild(clone);
		}

		start();

		// Pause while the guest is reading (hover/keyboard focus) — subtle,
		// no visible controls.
		var region = track.closest("[data-reviews-region]");
		if (region) {
			region.addEventListener("mouseenter", stop);
			region.addEventListener("mouseleave", start);
			region.addEventListener("focusin", stop);
			region.addEventListener("focusout", start);
		}

		// Respect viewport changes and motion-preference changes.
		window.addEventListener("resize", function () {
			applyOffset(false);
		});
		if (typeof reducedMotion.addEventListener === "function") {
			reducedMotion.addEventListener("change", function (e) {
				if (e.matches) {
					stop();
					track.classList.add("is-static");
					index = 0;
					applyOffset(false);
				} else {
					track.classList.remove("is-static");
					start();
				}
			});
		}
	}
})();
