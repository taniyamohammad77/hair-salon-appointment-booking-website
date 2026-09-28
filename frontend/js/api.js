/* ==========================================================================
   AMORÉ — API LAYER (integration step 1)
   --------------------------------------------------------------------------
   ONE place the frontend talks to the backend from. Future integration
   steps (services, stylists, availability, bookings, reviews) extend THIS
   file — page scripts must never hardcode URLs or call fetch() directly.

   Step-1 scope:
     - AMORE_API.BASE_URL      backend origin (matches backend CORS allowlist)
     - AMORE_API.checkHealth() GET /api/health with a timeout guard.
   Step-2 scope (catalogue):
     - AMORE_API.getServices()          GET /api/services
     - AMORE_API.getStylists(serviceId) GET /api/stylists[?serviceId=]

   Every call NEVER throws — resolves {ok, status, data, error} so callers
   can render an honest state instead of breaking the page.

   NO page calls this automatically yet — the UI keeps running on
   js/sample-data.js until each data source is wired (and its fallback
   removed) in later, separately approved steps. No visual changes.
   (Step 2: services + stylists catalogue now read through here, with
   sample-data as the documented fallback when the API is unavailable.)
   ========================================================================== */
(function () {
	"use strict";

	/**
	 * Live config + entry points. Backend origin must stay inside the
	 * backend's CORS allowlist (WebConfig: http://localhost:* /
	 * http://127.0.0.1:*). request() reads API.BASE_URL at call time, so
	 * tests (and later steps) can override window.AMORE_API.BASE_URL.
	 */
	var API = {
		BASE_URL: "http://localhost:8080",
		TIMEOUT_MS: 5000,
		request: request,
		checkHealth: checkHealth,
		getServices: getServices,
		getStylists: getStylists
	};

	/**
	 * Minimal JSON GET/POST/PUT core for later steps.
	 * Resolves {ok, status, data, error}; never rejects.
	 */
	function request(method, path, options) {
		options = options || {};
		var controller = typeof AbortController !== "undefined" ? new AbortController() : null;
		var timer = controller
			? setTimeout(function () { controller.abort(); }, API.TIMEOUT_MS)
			: null;

		return fetch(API.BASE_URL + path, {
			method: method,
			headers: options.body ? { "Content-Type": "application/json" } : undefined,
			body: options.body ? JSON.stringify(options.body) : undefined,
			signal: controller ? controller.signal : undefined,
			// rule 10: availability/visit data must never be served from the
			// browser's heuristic cache — identical GETs (e.g. a My Visits
			// refresh right after a reschedule) must hit the server fresh.
			cache: "no-store"
		})
			.then(function (res) {
				// 204 has no body; everything else is expected to be JSON
				// (backend always answers JSON, even for errors).
				return res.status === 204
					? null
					: res.json().catch(function () { return null; }).then(function (data) {
						return { res: res, data: data };
					});
			})
			.then(function (result) {
				if (timer) { clearTimeout(timer); }
				var res = result.res;
				var out = { ok: res.ok, status: res.status, data: result.data, error: null };
				if (!res.ok) {
					// Backend error shape: { "error": "human friendly message" }
					out.error = (result.data && result.data.error) || "Request failed (" + res.status + ").";
				}
				return out;
			})
			.catch(function (err) {
				if (timer) { clearTimeout(timer); }
				var reason = err && err.name === "AbortError"
					? "The server took too long to respond."
					: "Cannot reach the salon server right now.";
				return { ok: false, status: 0, data: null, error: reason };
			});
	}

	/** GET /api/health → {ok:true,status:200,data:{status:"up"}} when up. */
	function checkHealth() {
		return request("GET", "/api/health");
	}

	/** GET /api/services → [{id,name,description,category,durationMinutes,
	 *  bufferMinutes,price}] (backend ServiceDto). */
	function getServices() {
		return request("GET", "/api/services");
	}

	/** GET /api/stylists → [{id,name,role,bio,avatarUrl}] (backend
	 *  StylistDto). Optional serviceId filters to stylists offering that
	 *  service (backend 404s on unknown service ids — surfaced as
	 *  {ok:false,status:404}). */
	function getStylists(serviceId) {
		var path = "/api/stylists";
		if (serviceId !== undefined && serviceId !== null) {
			path += "?serviceId=" + encodeURIComponent(serviceId);
		}
		return request("GET", path);
	}

	window.AMORE_API = API;
})();
