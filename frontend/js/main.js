/* ==========================================================================
   FRONTEND FOUNDATION — PHASE 1 (behavior) + PHASE 4 (session bootstrap)
   --------------------------------------------------------------------------
   Scope: mobile nav toggle · sticky-header scrolled state · scroll reveals ·
   auth session revalidation (PHASE 4) + logged-in nav state.
   PHASE 4 — /api/auth/me revalidation: a stored token is NOT trusted; every
   page load asks the backend who it belongs to. Invalid/expired → honest
   clear (nav falls back to logged-out). No fake authenticated state ever.
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

	/* ======================================================================
	   4. AUTH SESSION BOOTSTRAP (PHASE 4)
	   Reflects the revalidated session in the nav: "Log in/Sign up" links
	   become "Hi, <first name> · Log out". Pages with their own gating
	   (booking.js) call AMORE_API.me() themselves — this markup update is
	   global so ALL pages show the honest state.
	   ====================================================================== */
	(function initAuthNav() {
		var loginLink = document.querySelector(".nav-login");
		var signupLink = document.querySelector(".nav-signup");
		if (!loginLink || typeof window.AMORE_API === "undefined" || !window.AMORE_API.me) {
			return; // markup absent (never on this design) or api.js not loaded
		}

		function renderLoggedOut() {
			// restore the original links (idempotent on repeat runs)
			loginLink.textContent = "Log in";
			loginLink.setAttribute("href", "login.html");
			if (signupLink) {
				signupLink.textContent = "Sign up";
				signupLink.setAttribute("href", "signup.html");
			}
			// a stale logged-in panel (if this ran before) goes away
			var out = document.querySelector("[data-nav-user]");
			if (out) { out.remove(); }
		}

		function renderLoggedIn(user) {
			var first = (user.name || "there").trim().split(/\s+/)[0];
			if (signupLink) {
				signupLink.textContent = "Hi, " + first;
				signupLink.setAttribute("href", "my-visits.html");
			}
			loginLink.textContent = "Log out";
			loginLink.setAttribute("href", "#");
		}

		if (!window.AMORE_API.isLoggedIn()) {
			renderLoggedOut();
			return;
		}

		// optimistic cache render, then the REAL check decides
		var cached = window.AMORE_API.getSessionUser();
		if (cached && cached.name) { renderLoggedIn(cached); }

		window.AMORE_API.me().then(function (res) {
			if (res.ok && res.data && res.data.email) {
				window.AMORE_API.setSession(window.AMORE_API.getToken(), res.data); // fresh identity cache
				renderLoggedIn(res.data);
			} else {
				// 401 (api.js already cleared the dead session) or server down:
				// honest logged-out state either way.
				window.AMORE_API.clearSession();
				renderLoggedOut();
			}
		});

		// Log out = clear session + reload (state comes from /me everywhere).
		loginLink.addEventListener("click", function (e) {
			if (!window.AMORE_API.isLoggedIn()) { return; } // href # only when logged in
			e.preventDefault();
			window.AMORE_API.logout();
			window.location.reload();
		});
	})();
})();
