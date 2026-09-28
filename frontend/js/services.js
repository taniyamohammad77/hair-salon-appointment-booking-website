/* ==========================================================================
   SERVICES SECTION (integration step 2)
   --------------------------------------------------------------------------
   Renders service cards through the shared renderers below. The DATA SOURCE
   is: real API (GET /api/services via js/api.js) when reachable, otherwise
   the documented sample fallback (js/sample-data.js) — never mixed, never
   fake-success: when the API is unavailable the polite live region says so
   and the fallback banner appears; no invented "live" claim is made.

   TWO MODES (auto-detected by page markup — unchanged):
   - Home section:      [data-services-grid] + [data-service-chips]
                        curated chips/grid, category filtering (unchanged).
   - Full menu page:    [data-service-groups] + [data-service-groups-status]
                        ALL services grouped by category, one group per
                        section — the full catalogue, no curation.

   With real API data the category grouping is preserved: groups are the
   approved SAMPLE_SERVICE_GROUPS labels/blurbs, but a group only renders
   if the BACKEND actually returned services in that category. Prices,
   durations and names always come from whichever data source was used.
   ========================================================================== */
(function () {
	"use strict";

	var grid = document.querySelector("[data-services-grid]");
	var chipsWrap = document.querySelector("[data-service-chips]");
	var emptyState = document.querySelector("[data-services-empty]");
	var statusEl = document.querySelector("[data-services-status]");

	/* ---------- shared helpers ---------- */

	/** INR price formatting: ₹1,499 (whole rupees; backend sends 2dp decimals). */
	function formatPrice(value) {
		return "₹" + Number(value).toLocaleString("en-IN", {
			maximumFractionDigits: 0
		});
	}

	function formatDuration(minutes) {
		return minutes + " min";
	}

	function escapeHtml(text) {
		var div = document.createElement("div");
		div.textContent = String(text);
		return div.innerHTML;
	}

	/** Shared reveal-on-scroll setup (subtle fade + rise, plays once). */
	function observeReveals(scope) {
		var els = scope.querySelectorAll(".reveal");
		if ("IntersectionObserver" in window) {
			var io = new IntersectionObserver(function (entries) {
				entries.forEach(function (entry) {
					if (entry.isIntersecting) {
						entry.target.classList.add("is-visible");
						io.unobserve(entry.target);
					}
				});
			}, { threshold: 0.1 });
			els.forEach(function (el) { io.observe(el); });
		} else {
			els.forEach(function (el) { el.classList.add("is-visible"); });
		}
	}

	/** Same card markup for both modes (identical shape contract). */
	function cardEl(service) {
		var card = document.createElement("article");
		card.className = "service-card reveal";
		card.setAttribute("data-category", service.category);

		var titleId = "service-title-" + service.id;
		card.setAttribute("aria-labelledby", titleId);

		card.innerHTML =
			'<span class="service-card-category">' + escapeHtml(service.category) + "</span>" +
			'<h3 class="service-card-title" id="' + titleId + '">' + escapeHtml(service.name) + "</h3>" +
			'<p class="service-card-desc">' + escapeHtml(service.description || "") + "</p>" +
			'<div class="service-card-meta">' +
			'<span class="service-card-duration">' + formatDuration(service.durationMinutes) + "</span>" +
			'<span class="service-card-price">' + formatPrice(service.price) + "</span>" +
			"</div>" +
			'<button class="btn btn-accent service-card-book" type="button" ' +
			'data-book-id="' + service.id + '">Book</button>';

		// Phase 5: deep-link into the booking wizard with this service preselected.
		card.querySelector("[data-book-id]").addEventListener("click", function () {
			window.location.href = "booking.html?service=" + encodeURIComponent(service.id);
		});

		return card;
	}

	function announce(message) {
		var el = statusEl || document.querySelector("[data-service-groups-status]");
		if (el) {
			el.textContent = message; // polite live region for screen readers
		}
	}

	/** One shared banner element for honest fallback/error notes. */
	function bannerEl() {
		var el = document.querySelector("[data-api-note]");
		if (!el) {
			el = document.createElement("p");
			el.className = "api-note";
			el.setAttribute("data-api-note", "");
			el.setAttribute("role", "status");
			if (statusEl && statusEl.parentNode) {
				statusEl.parentNode.insertBefore(el, statusEl.nextSibling);
			} else {
				var groupsWrap = document.querySelector("[data-service-groups]");
				if (groupsWrap && groupsWrap.parentNode) {
					groupsWrap.parentNode.insertBefore(el, groupsWrap);
				}
			}
		}
		return el;
	}

	function showBanner(message) {
		bannerEl().textContent = message;
	}

	function hideBanner() {
		var el = document.querySelector("[data-api-note]");
		if (el) {
			el.textContent = "";
		}
	}

	/**
	 * THE data source: API first (rule 10 — real data when reachable),
	 * sample fallback when not. Resolves
	 * { services, source: "api"|"sample", error: string|null }.
	 */
	function loadServices() {
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.getServices) {
			return Promise.resolve({
				services: window.SAMPLE_SERVICES || [],
				source: "sample",
				error: null
			});
		}
		return window.AMORE_API.getServices().then(function (res) {
			if (res.ok && Array.isArray(res.data)) {
				return { services: res.data, source: "api", error: null };
			}
			// API reachable but unhappy (5xx/shape) or unreachable → fallback
			return {
				services: window.SAMPLE_SERVICES || [],
				source: "sample",
				error: (res.error && typeof res.error === "string") ? res.error : "The salon server could not load services."
			};
		});
	}

	/* ======================================================================
	   MODE A — HOME SECTION: curated grid + category filter chips
	   ====================================================================== */

	function initHomeSection() {
		if (!grid || typeof window.SAMPLE_SERVICES === "undefined") {
			return;
		}

		var CATEGORY_LABELS = {
			All: "All services",
			Haircut: "Haircuts",
			Beard: "Beard",
			Colour: "Colour",
			Styling: "Styling",
			Treatment: "Treatments"
		};

		var allServices = [];   // full list from whichever source loaded
		var source = "sample";  // "api" | "sample"
		var sourceError = null;

		function render(list) {
			grid.innerHTML = "";
			list.forEach(function (service) {
				grid.appendChild(cardEl(service));
			});
			observeReveals(grid);
		}

		var activeCategory = "All";

		function applyFilter() {
			// HOME CURATION (user decision): with real API data the curated
			// set is the first four services in API (name-asc) order — the
			// full menu lives on the services page. Sample fallback keeps
			// the original SAMPLE_HOME_SERVICE_IDS curation.
			var curated;
			if (source === "api") {
				curated = allServices.slice(0, 4);
			} else {
				var homeIds = window.SAMPLE_HOME_SERVICE_IDS || null;
				curated = allServices.filter(function (service) {
					return !homeIds || homeIds.indexOf(service.id) !== -1;
				});
			}
			var list = curated.filter(function (service) {
				return activeCategory === "All" || service.category === activeCategory;
			});
			render(list);
			// "All" with the full curated set: cards in one straight row
			// (plain grid — no carousel, no autoplay). Same as before.
			grid.classList.toggle("is-single-row",
				activeCategory === "All" && list.length === 4);
			if (emptyState) {
				emptyState.hidden = list.length > 0;
			}
			announce((CATEGORY_LABELS[activeCategory] || activeCategory) + ": " +
				list.length + " service" + (list.length === 1 ? "" : "s"));
		}

		if (chipsWrap) {
			chipsWrap.addEventListener("click", function (e) {
				var chip = e.target.closest("[data-category-chip]");
				if (!chip) {
					return;
				}
				activeCategory = chip.getAttribute("data-category-chip");
				chipsWrap.querySelectorAll("[data-category-chip]").forEach(function (c) {
					var selected = c === chip;
					c.classList.toggle("is-active", selected);
					c.setAttribute("aria-pressed", selected ? "true" : "false");
				});
				applyFilter();
			});
		}

		function showSkeleton(count) {
			grid.innerHTML = "";
			if (emptyState) {
				emptyState.hidden = true;
			}
			for (var i = 0; i < count; i++) {
				var skeleton = document.createElement("div");
				skeleton.className = "service-card service-card-skeleton";
				skeleton.setAttribute("aria-hidden", "true");
				skeleton.innerHTML =
					'<span class="skeleton-line skeleton-chip"></span>' +
					'<span class="skeleton-line skeleton-title"></span>' +
					'<span class="skeleton-line"></span>' +
					'<span class="skeleton-line skeleton-short"></span>' +
					'<span class="skeleton-line skeleton-meta"></span>';
				grid.appendChild(skeleton);
			}
		}

		showSkeleton(6);

		// API-first load. No artificial delay — the network provides the
		// observable loading state now.
		loadServices().then(function (result) {
			allServices = result.services;
			source = result.source;
			sourceError = result.error;
			applyFilter();

			if (source === "api") {
				hideBanner();
				announce("Live services loaded from the salon server.");
			} else if (sourceError) {
				showBanner("Live service menu unavailable right now (" + sourceError +
					" Showing our standard sample menu.");
				announce("Live service menu unavailable — showing the sample menu.");
			} else {
				hideBanner();
			}
		});
	}

	/* ======================================================================
	   MODE B — FULL MENU PAGE: all services grouped by category
	   ====================================================================== */

	function initFullMenu() {
		var groupsWrap = document.querySelector("[data-service-groups]");
		if (!groupsWrap || typeof window.SAMPLE_SERVICES === "undefined" ||
				typeof window.SAMPLE_SERVICE_GROUPS === "undefined") {
			return;
		}

		// Hide the entire skeleton block once real content is ready.
		var skeletonBlock = document.querySelector("[data-groups-skeleton]");
		if (skeletonBlock) {
			skeletonBlock.hidden = true;
		}

		var services = [];
		var sourceError = null;

		function renderGroups() {
			var servicesByCategory = {};
			services.forEach(function (s) {
				(servicesByCategory[s.category] = servicesByCategory[s.category] || []).push(s);
			});

			// Approved grouping preserved: labels/blurbs from
			// SAMPLE_SERVICE_GROUPS, but a group renders only when the
			// loaded data actually has services in that category.
			var groups = window.SAMPLE_SERVICE_GROUPS.filter(function (g) {
				return (servicesByCategory[g.category] || []).length > 0;
			});

			if (groups.length === 0) {
				announce("The salon server returned no services right now.");
				return;
			}

			groups.forEach(function (group) {
				var list = servicesByCategory[group.category] || [];
				var section = document.createElement("article");
				section.className = "service-group reveal";
				section.id = "cat-" + group.category.toLowerCase();
				section.setAttribute("aria-labelledby", "cat-title-" + group.category.toLowerCase());

				section.innerHTML =
					'<header class="service-group-header">' +
					'<div>' +
					'<h2 class="service-group-title" id="cat-title-' + group.category.toLowerCase() + '">' + escapeHtml(group.title) + '</h2>' +
					'<p class="service-group-blurb">' + escapeHtml(group.blurb) + "</p>" +
					'</div>' +
					'<span class="service-group-count">' + list.length + (list.length === 1 ? " service" : " services") + "</span>" +
					"</header>" +
					'<div class="services-grid services-grid--group"></div>';

				var gridEl = section.querySelector(".services-grid--group");
				list.forEach(function (service) {
					gridEl.appendChild(cardEl(service));
				});

				groupsWrap.appendChild(section);
				announce(group.title + ": " + list.length + " loaded");
			});

			observeReveals(groupsWrap);
		}

		loadServices().then(function (result) {
			services = result.services;
			sourceError = result.error;

			if (result.source === "api") {
				hideBanner();
				announce("Live service menu loaded from the salon server.");
			} else if (sourceError) {
				showBanner("Live service menu unavailable right now (" + sourceError +
					" Showing our standard sample menu.");
				announce("Live service menu unavailable — showing the sample menu.");
			} else {
				hideBanner();
			}
			renderGroups();
		});
	}

	// Run exactly one mode per page (markup decides, not URL sniffing).
	if (grid && chipsWrap) {
		initHomeSection();
	} else {
		initFullMenu();
	}
})();
