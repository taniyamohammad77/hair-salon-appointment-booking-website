/* ==========================================================================
   STYLISTS SECTION (integration step 2)
   --------------------------------------------------------------------------
   Renders stylist cards through the shared renderers below. The DATA SOURCE
   is: real API (GET /api/stylists via js/api.js) when reachable, otherwise
   the documented sample fallback (js/sample-data.js) — never mixed, never
   fake-success. An API that answers with an EMPTY list renders an honest
   empty state; it never falls back to invented names.

   Specialty chips are still derived from the junction meta in
   sample-data.js (SAMPLE_STYLIST_SERVICES + SAMPLE_SERVICE_CATEGORIES):
   the stylists_services junction has NO public endpoint yet, and the meta
   matches the backend seed rows 1:1. This is documented metadata, not fake
   live data — names/roles/bios always come from the winning data source.

   Monogram avatars remain the honest fallback until real avatarUrl values
   exist — the markup is image-ready (an <img> is swapped in when the API
   supplies a URL).
   ========================================================================== */
(function () {
	"use strict";

	var grid = document.querySelector("[data-stylists-grid]");

	if (!grid) {
		return; // section not on this page
	}

	var services = typeof window.SAMPLE_STYLIST_SERVICES !== "undefined"
		? window.SAMPLE_STYLIST_SERVICES
		: [];
	var categories = typeof window.SAMPLE_SERVICE_CATEGORIES !== "undefined"
		? window.SAMPLE_SERVICE_CATEGORIES
		: {};

	/* ---------- helpers ---------- */

	function escapeHtml(text) {
		var div = document.createElement("div");
		div.textContent = String(text);
		return div.innerHTML;
	}

	/** Distinct specialty categories for one stylist, in a stable order. */
	function specialtiesFor(stylistId) {
		var seen = {};
		var list = [];
		services.forEach(function (row) {
			if (row.stylistId !== stylistId) {
				return;
			}
			var category = categories[row.serviceId];
			if (category && !seen[category]) {
				seen[category] = true;
				list.push(category);
			}
		});
		// stable, readable order
		var order = ["Haircut", "Beard", "Colour", "Styling", "Treatment"];
		list.sort(function (a, b) {
			return order.indexOf(a) - order.indexOf(b);
		});
		return list;
	}

	/** Avatar: real photo when avatarUrl exists, monogram otherwise. */
	function avatarHtml(stylist) {
		var initials = escapeHtml(stylist.name.trim().charAt(0).toUpperCase());
		if (stylist.avatarUrl) {
			return '<img class="stylist-avatar-img" src="' + escapeHtml(stylist.avatarUrl) +
				'" alt="Portrait of ' + escapeHtml(stylist.name) + '" loading="lazy" />';
		}
		return '<span class="stylist-avatar-letter" aria-hidden="true">' + initials + "</span>";
	}

	/* ---------- card rendering (unchanged markup) ---------- */

	function cardEl(stylist) {
		var card = document.createElement("article");
		card.className = "stylist-card reveal";
		var titleId = "stylist-title-" + stylist.id;
		card.setAttribute("aria-labelledby", titleId);

		var chips = specialtiesFor(stylist.id)
			.map(function (category) {
				return '<span class="stylist-chip">' + escapeHtml(category) + "</span>";
			})
			.join("");

		card.innerHTML =
			'<div class="stylist-avatar">' + avatarHtml(stylist) + "</div>" +
			'<h3 class="stylist-name" id="' + titleId + '">' + escapeHtml(stylist.name) + "</h3>" +
			'<p class="stylist-role">' + escapeHtml(stylist.role || "") + "</p>" +
			'<p class="stylist-bio">' + escapeHtml(stylist.bio || "") + "</p>" +
			(chips ? '<div class="stylist-chips" aria-label="Specialties">' + chips + "</div>" : "") +
			// Phase 5: deep-link into the booking wizard with this stylist preselected
			'<div class="stylist-card-actions">' +
			'<a class="btn btn-ghost stylist-book" href="booking.html?stylist=' + stylist.id + '">Book with ' +
			escapeHtml(stylist.name.split(" ")[0]) + "</a></div>";

		return card;
	}

	function render(list) {
		grid.innerHTML = "";
		list.forEach(function (stylist) {
			grid.appendChild(cardEl(stylist));
		});

		// Same site-wide reveal used by Services (fade + rise, once).
		var cards = grid.querySelectorAll(".reveal");
		if ("IntersectionObserver" in window) {
			var io = new IntersectionObserver(function (entries) {
				entries.forEach(function (entry) {
					if (entry.isIntersecting) {
						entry.target.classList.add("is-visible");
						io.unobserve(entry.target);
					}
				});
			}, { threshold: 0.1 });
			cards.forEach(function (el) { io.observe(el); });
		} else {
			cards.forEach(function (el) { el.classList.add("is-visible"); });
		}
	}

	/* ---------- honest note (shared pattern with services.js) ---------- */

	function bannerEl() {
		var el = document.querySelector("[data-api-note]");
		if (!el) {
			el = document.createElement("p");
			el.className = "api-note";
			el.setAttribute("data-api-note", "");
			el.setAttribute("role", "status");
			if (grid.parentNode) {
				grid.parentNode.insertBefore(el, grid.nextSibling);
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

	/* ---------- data source: API first, documented fallback ---------- */

	function announce(message) {
		// reuse the services live region if present (home page), else none
		var el = document.querySelector("[data-services-status]") ||
			document.querySelector("[data-service-groups-status]");
		if (el) {
			el.textContent = message;
		}
	}

	function loadStylists(serviceId) {
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.getStylists) {
			return Promise.resolve({
				stylists: window.SAMPLE_STYLISTS || [],
				source: "sample",
				error: null
			});
		}
		return window.AMORE_API.getStylists(serviceId).then(function (res) {
			if (res.ok && Array.isArray(res.data)) {
				return { stylists: res.data, source: "api", error: null };
			}
			return {
				stylists: window.SAMPLE_STYLISTS || [],
				source: "sample",
				error: (res.error && typeof res.error === "string") ? res.error : "The salon server could not load stylists."
			};
		});
	}

	/* ---------- init ---------- */

	loadStylists().then(function (result) {
		render(result.stylists);

		if (result.source === "api") {
			hideBanner();
			if (result.stylists.length === 0) {
				announce("The salon has no stylists available right now.");
			} else {
				announce("Live stylist team loaded from the salon server.");
			}
		} else if (result.error) {
			showBanner("Live stylist team unavailable right now (" + result.error +
				" Showing our standard sample team.");
			announce("Live stylist team unavailable — showing the sample team.");
		} else {
			hideBanner();
		}
	});
})();
