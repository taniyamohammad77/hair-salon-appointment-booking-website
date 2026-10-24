/* ==========================================================================
   PHASE 5 — EMAIL VERIFICATION PAGE (verify.html)
   --------------------------------------------------------------------------
   Three entry modes, read from the URL — never invented:
     - ?token=<t>     the emailed link: GET /api/auth/verify?token= is
                      called ONCE, then the result is rendered honestly
                      (success / expired / already used / unreachable).
                      On success the token is stripped from the URL so a
                      refresh can't replay a consumed link and show a
                      confusing "invalid" error.
     - ?pending=1     redirect target after login for an UNVERIFIED
                      account: explains the state, offers Continue (?next=)
                      and a resend of the link to the session email.
     - neither        generic honest instructions + the resend form.
   The resend form posts through AMORE_API.resendVerification; the server
   answers with ONE generic message whether or not the address exists (no
   account enumeration) and rate-limits to 5/min/IP — its exact wording is
   shown as-is, including 429 errors.
   Every string from the server is written with textContent (never HTML).
   ========================================================================== */
(function () {
	"use strict";

	var checkingEl = document.querySelector("[data-verify-checking]");
	var resultEl = document.querySelector("[data-verify-result]");
	var pendingEl = document.querySelector("[data-verify-pending]");
	var genericEl = document.querySelector("[data-verify-generic]");
	var resendForm = document.querySelector("[data-resend-form]");
	if (!checkingEl || !resultEl || !pendingEl || !genericEl || !resendForm) {
		return; // not on the verify page
	}

	var iconEl = document.querySelector("[data-verify-icon]");
	var titleEl = document.querySelector("[data-verify-title]");
	var messageEl = document.querySelector("[data-verify-message]");
	var primaryEl = document.querySelector("[data-verify-primary]");
	var secondaryEl = document.querySelector("[data-verify-secondary]");
	var pendingTextEl = document.querySelector("[data-verify-pending-text]");
	var continueEl = document.querySelector("[data-verify-continue]");
	var emailInput = document.querySelector("[data-resend-email]");
	var resendStatus = document.querySelector("[data-resend-status]");
	var altEl = document.querySelector("[data-verify-alt]");
	var statusEl = document.querySelector("[data-verify-status]");

	var params = new URLSearchParams(window.location.search);
	var token = params.get("token");
	var fromLogin = params.get("pending") === "1";

	function announce(text) {
		if (statusEl) { statusEl.textContent = text; }
	}

	/* Where "Continue" should land — same same-origin guard as auth.js
	   (?next= survives the login → verify → destination round-trip; any
	   cross-origin or malformed value falls back to index.html). */
	function nextDestination() {
		try {
			var next = params.get("next");
			if (next) {
				var url = new URL(next, window.location.origin);
				if (url.origin === window.location.origin) { return url.pathname + url.search; }
			}
		} catch (e) { /* malformed next — default below */ }
		return "index.html";
	}

	function sessionEmail() {
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.getSessionUser) {
			return "";
		}
		var user = window.AMORE_API.getSessionUser();
		return (user && user.email) ? user.email : "";
	}

	function showOnly(block) {
		[checkingEl, resultEl, pendingEl, genericEl].forEach(function (el) {
			el.hidden = el !== block;
		});
	}

	function showResendForm(prefill, altVisible) {
		if (prefill && emailInput) { emailInput.value = prefill; }
		resendForm.hidden = false;
		if (altEl) { altEl.hidden = !altVisible; }
	}

	function showResendStatus(text) {
		if (!resendStatus) { return; }
		resendStatus.textContent = text;
		resendStatus.hidden = !text;
	}

	/* ------------------------------------------------------------------
	   MODE 1 — token from the emailed link (runs exactly once)
	   ------------------------------------------------------------------ */
	if (token) {
		showOnly(checkingEl);
		announce("Verifying your email address.");

		window.AMORE_API.verifyEmail(token).then(function (res) {
			if (res.ok) {
				// Strip the now-consumed token: a refresh must not replay it.
				try {
					window.history.replaceState(null, "", window.location.pathname);
				} catch (e) { /* history unavailable — harmless */ }
				showOnly(resultEl);
				if (iconEl) { iconEl.textContent = "✓"; }
				if (titleEl) { titleEl.textContent = "Email verified!"; }
				if (messageEl) { messageEl.textContent = res.data.message; }
				var loggedIn = window.AMORE_API.isLoggedIn();
				if (primaryEl) {
					primaryEl.textContent = loggedIn ? "Continue" : "Log in";
					primaryEl.setAttribute("href", loggedIn ? nextDestination() : "login.html");
				}
				if (secondaryEl) { secondaryEl.hidden = false; }
				announce("Email verified. " + res.data.message);
				return;
			}
			if (res.status === 0) {
				// Network/server unreachable — the token was NOT consumed,
				// so reloading to retry is safe and honest.
				showOnly(resultEl);
				if (iconEl) { iconEl.textContent = "✉"; }
				if (titleEl) { titleEl.textContent = "We couldn't reach the server"; }
				if (messageEl) { messageEl.textContent = res.error; }
				if (primaryEl) {
					primaryEl.textContent = "Try again";
					primaryEl.setAttribute("href", window.location.pathname + window.location.search);
				}
				if (secondaryEl) { secondaryEl.hidden = false; }
				announce(res.error);
				return;
			}
			// 400 — invalid / already used / expired (server's exact wording)
			showOnly(resultEl);
			if (iconEl) { iconEl.textContent = "✉"; }
			if (titleEl) { titleEl.textContent = "We couldn't verify that link"; }
			if (messageEl) { messageEl.textContent = res.error; }
			if (primaryEl) {
				primaryEl.textContent = "Log in";
				primaryEl.setAttribute("href", "login.html");
			}
			if (secondaryEl) { secondaryEl.hidden = false; }
			announce(res.error);
			// Offer a fresh link (prefilled when this browser has a session).
			showResendForm(sessionEmail(), false);
			showResendStatus("");
		});
		return;
	}

	/* ------------------------------------------------------------------
	   MODE 2 — logged in but unverified (?pending=1 from the login flow)
	   ------------------------------------------------------------------ */
	if (fromLogin && window.AMORE_API.isLoggedIn()) {
		var email = sessionEmail();
		showOnly(pendingEl);
		if (pendingTextEl) {
			pendingTextEl.textContent = email
				? "We sent a verification link to " + email
						+ ". Open it to verify your account before booking/reviews — "
						+ "or request a fresh link below if it expired."
				: "We sent you a verification link. Open it to verify your account "
						+ "before booking/reviews — or request a fresh link below.";
		}
		if (continueEl) { continueEl.setAttribute("href", nextDestination()); }
		showResendForm(email, false);
		announce("Your email address still needs verification.");
		return;
	}

	/* ------------------------------------------------------------------
	   MODE 3 — no usable token/session: honest instructions
	   ------------------------------------------------------------------ */
	showOnly(genericEl);
	showResendForm(sessionEmail(), true);
	announce("Check your email for your verification link.");
})();

/* ==========================================================================
   RESEND FORM (shared by all modes) — lives outside the IIFE only for
   readability; it binds after the mode logic above has revealed the form.
   ========================================================================== */
(function () {
	"use strict";

	var resendForm = document.querySelector("[data-resend-form]");
	if (!resendForm) { return; }

	var emailInput = resendForm.querySelector("[data-resend-email]");
	var resendError = resendForm.querySelector("[data-resend-error]");
	var resendBtn = resendForm.querySelector("[data-resend-submit]");
	var resendStatus = document.querySelector("[data-resend-status]");

	var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

	function setFieldError(message) {
		if (!emailInput || !resendError) { return; }
		if (message) {
			emailInput.setAttribute("aria-invalid", "true");
			resendError.textContent = message;
			resendError.hidden = false;
			return;
		}
		emailInput.removeAttribute("aria-invalid");
		resendError.hidden = true;
	}

	function showResendStatus(text) {
		if (!resendStatus) { return; }
		resendStatus.textContent = text;
		resendStatus.hidden = !text;
	}

	resendForm.addEventListener("submit", function (e) {
		e.preventDefault();
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.resendVerification) {
			showResendStatus("The salon server could not be reached. Please try again.");
			return;
		}

		var value = emailInput.value.trim();
		var message = "";
		if (!value) { message = "Email is required."; }
		else if (value.length > 160) { message = "Email must be 160 characters or fewer."; }
		else if (!EMAIL_RE.test(value)) { message = "Enter a valid email address."; }
		if (message) {
			setFieldError(message);
			showResendStatus("");
			emailInput.focus();
			return;
		}
		setFieldError("");
		showResendStatus("");

		resendBtn.disabled = true;
		resendBtn.setAttribute("aria-busy", "true");
		showResendStatus("Sending…");

		window.AMORE_API.resendVerification(value).then(function (res) {
			resendBtn.disabled = false;
			resendBtn.removeAttribute("aria-busy");
			if (res.ok) {
				// Server's generic message — identical whether or not the
				// account exists (anti-enumeration by design).
				showResendStatus(res.data.message);
				return;
			}
			// 400 field error, 429 rate limit, network — shown as-is.
			var fieldMsg = res.data && res.data.fields && res.data.fields.email;
			if (fieldMsg) { setFieldError(fieldMsg); showResendStatus(""); return; }
			showResendStatus(res.error);
		});
	});
})();
