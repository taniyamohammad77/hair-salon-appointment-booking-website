/* ==========================================================================
   REVIEWS SECTION — HOMEPAGE (PHASE 4: wired to the real API)
   --------------------------------------------------------------------------
   Data source: real GET /api/reviews (through js/api.js) when the backend
   is reachable. Honest states only (user rule 5/10):
     - loading note while the request is in flight
     - real reviews -> render whatever length the API returns (NEVER
       hardcoded to 6), then start the carousel
     - 0 real reviews -> honest empty state ("No reviews yet — be the
       first!"), no cards, NO fabricated testimonials
     - API unavailable -> the documented DEV-ONLY sample fallback
       (js/sample-data.js, SAMPLE_REVIEWS) behind the honest preview
       banner — samples are never passed off as real guest stories

   Carousel behaviour (unchanged): premium, subtle auto-move — one card at
   a time, continuous loop, NO visible controls; pause on hover/focus for
   keyboard readability; `prefers-reduced-motion: reduce` -> NO automatic
   movement, a readable static state instead (CSS handles the visual, JS
   skips the timer).
   ========================================================================== */
(function () {
	"use strict";

	var track = document.querySelector("[data-reviews-track]");
	var bannerEl = document.querySelector("[data-reviews-banner]");
	var loadingEl = document.querySelector("[data-reviews-loading]");
	var emptyEl = document.querySelector("[data-reviews-empty]");

	if (!track) {
		return; // reviews section is not on this page
	}

	var SLIDE_INTERVAL_MS = 4500;
	var TRANSITION_MS = 600;
	var reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
	var carouselStarted = false;

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
		// cards are focusable so keyboard users can actually enter the
		// reviews region — that is what makes the focusin-pause reachable.
		card.setAttribute("tabindex", "0");

		// comment is optional on the backend (null allowed) — the quote is
		// only rendered when the guest actually wrote one.
		var quote = review.comment
			? '<blockquote class="review-quote">"' + escapeHtml(review.comment) + '"</blockquote>'
			: "";
		// real API data carries BOTH names; the legacy sample shape only
		// had serviceName — show whatever is honestly available.
		var metaBits = [];
		if (review.serviceName) { metaBits.push(escapeHtml(review.serviceName)); }
		if (review.stylistName) { metaBits.push(escapeHtml(review.stylistName)); }

		card.innerHTML =
			'<div class="review-stars" role="img" aria-label="Rated ' + review.rating +
			' out of 5">' + starsHtml(review.rating) + "</div>" +
			quote +
			'<footer class="review-meta">' +
			'<cite class="review-guest" id="' + titleId + '">' + escapeHtml(review.guestName || "Guest") + "</cite>" +
			(metaBits.length ? '<span class="review-service">' + metaBits.join(" \u00B7 ") + "</span>" : "") +
			"</footer>";

		return card;
	}

	function renderList(reviews) {
		track.innerHTML = "";
		reviews.forEach(function (review) {
			track.appendChild(cardEl(review));
		});
	}

	/* ---------- honest state elements (prepared in index.html) ---------- */

	function hideLoading() {
		if (loadingEl) { loadingEl.textContent = ""; }
	}

	function showLoading(text) {
		if (loadingEl) { loadingEl.textContent = text; }
	}

	function showEmptyState() {
		if (emptyEl) { emptyEl.hidden = false; }
	}

	function showPreviewBanner() {
		if (bannerEl) { bannerEl.hidden = false; }
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

	/** Wire the carousel to whatever cards are currently in the track.
	 *  Runs at most once per page; safe to call after data arrives. */
	function setupCarousel() {
		if (carouselStarted) { return; }
		carouselStarted = true;

		if (reducedMotion.matches) {
			// Static, readable state — CSS also switches the layout for this case.
			track.classList.add("is-static");
			return;
		}

		// Seamless loop: clone the first visible set of cards to the end.
		var visible = visibleCount();
		for (var i = 0; i < visible; i++) {
			var clone = track.children[i].cloneNode(true);
			clone.setAttribute("aria-hidden", "true");
			// clones must not be keyboard-focusable (aria-hidden + focusable
			// = violation) and must not duplicate the cite's id.
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

	/* ---------- data: real API first, documented sample fallback ---------- */

	/** API unreachable (or the api layer is missing): render the DEV-ONLY
	 *  samples behind the honest preview banner. Never fake-success. */
	function useSampleFallback() {
		hideLoading();
		if (typeof window.SAMPLE_REVIEWS === "undefined" || window.SAMPLE_REVIEWS.length === 0) {
			showLoading("Guest reviews are unavailable right now \u2014 please check back soon.");
			return;
		}
		showPreviewBanner();
		renderList(window.SAMPLE_REVIEWS);
		setupCarousel();
	}

	function load() {
		showLoading("Loading guest reviews\u2026");

		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.request) {
			useSampleFallback();
			return;
		}

		window.AMORE_API.request("GET", "/api/reviews").then(function (res) {
			hideLoading();
			if (res.ok && Array.isArray(res.data)) {
				if (res.data.length === 0) {
					// honest empty state — NEVER invented testimonials
					showEmptyState();
					return;
				}
				renderList(res.data); // count = whatever the API returned
				setupCarousel();
				return;
			}
			// down / 4xx / 5xx — documented fallback, clearly labelled
			useSampleFallback();
		});
	}

	load();
})();
