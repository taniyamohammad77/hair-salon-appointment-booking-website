/* ==========================================================================
   FRONTEND FOUNDATION — PHASE 1 (behavior)
   --------------------------------------------------------------------------
   Scope: mobile nav toggle · sticky-header scrolled state · scroll reveals.
   No backend calls. No sample data (that arrives in an approved later phase,
   in its own clearly separated module).
   ========================================================================== */
(function () {
	"use strict";

	/* ---------- 1. Mobile navigation toggle ---------- */
	var toggle = document.querySelector("[data-nav-toggle]");
	var nav = document.querySelector("[data-nav]");

	if (toggle && nav) {
		toggle.addEventListener("click", function () {
			var open = nav.classList.toggle("is-open");
			toggle.setAttribute("aria-expanded", open ? "true" : "false");
			toggle.setAttribute("aria-label", open ? "Close menu" : "Open menu");
		});

		// Close the panel after choosing a destination (mobile UX)
		nav.addEventListener("click", function (e) {
			if (e.target.closest("a")) {
				nav.classList.remove("is-open");
				toggle.setAttribute("aria-expanded", "false");
			}
		});

		// Reset state when returning to desktop width
		window.addEventListener("resize", function () {
			if (window.innerWidth > 720 && nav.classList.contains("is-open")) {
				nav.classList.remove("is-open");
				toggle.setAttribute("aria-expanded", "false");
			}
		});
	}

	/* ---------- 2. Sticky header scrolled state ---------- */
	var header = document.querySelector("[data-header]");

	if (header) {
		var onScroll = function () {
			header.classList.toggle("is-scrolled", window.scrollY > 8);
		};
		window.addEventListener("scroll", onScroll, { passive: true });
		onScroll();
	}

	/* ---------- 3. Scroll reveals (fade + rise, plays once) ---------- */
	var revealEls = document.querySelectorAll(".reveal");

	if ("IntersectionObserver" in window && revealEls.length > 0) {
		var io = new IntersectionObserver(
			function (entries) {
				entries.forEach(function (entry) {
					if (entry.isIntersecting) {
						entry.target.classList.add("is-visible");
						io.unobserve(entry.target);
					}
				});
			},
			{ threshold: 0.15, rootMargin: "0px 0px -40px 0px" }
		);
		revealEls.forEach(function (el) {
			io.observe(el);
		});
	} else {
		// Fallback: show everything immediately
		revealEls.forEach(function (el) {
			el.classList.add("is-visible");
		});
	}
})();
