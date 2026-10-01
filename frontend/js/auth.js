/* ==========================================================================
   AUTH UI (Phase 7 UI + AUTH PHASE 4 real submit) — LOGIN + SIGNUP
   --------------------------------------------------------------------------
   PHASE 4: the same markup now talks to the real backend via the ONE API
   layer (AMORE_API.signup/login). Validation rules are UNCHANGED; a valid
   submit now reaches the server:
     - LOGIN: wrong credentials → honest inline error, form stays. Success →
       session stored by api.js, then a REDIRECT to the "next" destination
       (login.html?next=<encoded> — set by the booking gate so deep links
       like ?service=&stylist= survive the round-trip; default index.html).
     - SIGNUP: success → confirmation panel (same card, no redesign) with a
       direct "Log in" link (no auto-login — the user then logs in and is
       returned to where they were headed via the same ?next= mechanism).
       409 duplicate email → honest inline error.
   The old "coming soon" panel is gone from the submit path — this page can
   no longer pretend auth is unavailable. Passwords are never stored; the
   token lives in sessionStorage via api.js only.
   ========================================================================== */
(function () {
	"use strict";

	var form = document.querySelector("[data-auth-form]");
	var coming = document.querySelector("[data-coming-soon]");
	var statusEl = document.querySelector("[data-auth-status]");
	if (!form || !coming) {
		return; // not on an auth page
	}

	var isSignup = !!form.querySelector("[data-confirm]");

	/* PHASE 4: where should a successful auth flow land? The booking gate
	   links here with ?next=<full URL> — only same-origin http(s) URLs are
	   honored (open-redirect protection); anything else → index.html. */
	function nextDestination() {
		try {
			var next = new URLSearchParams(window.location.search).get("next");
			if (next) {
				var url = new URL(next, window.location.origin);
				if (url.origin === window.location.origin) { return url.pathname + url.search; }
			}
		} catch (e) { /* malformed next — fall through to default */ }
		return "index.html";
	}

	/* First error from the backend's {error, fields:{name:msg}} shape → the
	   existing inline error elements (no redesign; same ladder as client
	   validation). Returns true when an error was rendered. */
	function renderFieldErrors(fields) {
		if (!fields) { return false; }
		var firstInput = null;
		Object.keys(fields).forEach(function (key) {
			var map = { name: "[data-name]", email: "[data-email]", password: "[data-password]", confirmPassword: "[data-confirm]" };
			var input = form.querySelector(map[key]);
			if (input) {
				var f = field(input);
				if (f.err) { setError(input, f.err, fields[key]); }
				if (!firstInput) { firstInput = input; }
			}
		});
		if (firstInput) { firstInput.focus(); }
		return !!firstInput;
	}

	var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

	function esc(text) {
		var d = document.createElement("div");
		d.textContent = String(text);
		return d.innerHTML;
	}

	function announce(text) {
		if (statusEl) { statusEl.textContent = text; }
	}

	/* ---------- error rendering (aria-invalid + describedby connection) ---------- */
	function setError(input, errEl, message) {
		if (message) {
			input.setAttribute("aria-invalid", "true");
			errEl.textContent = message;
			errEl.hidden = false;
			return false;
		}
		input.removeAttribute("aria-invalid");
		errEl.hidden = true;
		return true;
	}

	/* error <p> lives directly AFTER its input's wrapper (login: sibling of
	   the .password-field div; signup: sibling too). Resolve at call time —
	   selectors were intentionally kept OUT of the markup to avoid dup ids. */
	function field(el) {
		var container = el.closest(".form-field");
		return {
			input: el,
			err: container ? container.querySelector(".field-error") : null
		};
	}

	/* ---------- per-field validators (return "" when valid) ---------- */
	function validateEmail() {
		var f = field(form.querySelector("[data-email]"));
		var v = f.input.value.trim();
		var msg = "";
		if (!v) { msg = "Email is required."; }
		else if (v.length > 160) { msg = "Email must be 160 characters or fewer."; }
		else if (!EMAIL_RE.test(v)) { msg = "Enter a valid email address."; }
		return setError(f.input, f.err, msg);
	}

	function validatePassword() {
		var f = field(form.querySelector("[data-password]"));
		var msg = f.input.value ? "" : "Password is required.";
		return setError(f.input, f.err, msg);
	}

	function validateName() {
		var f = field(form.querySelector("[data-name]"));
		var v = f.input.value.trim();
		var msg = "";
		if (!v) { msg = "Name is required."; }
		else if (v.length < 2) { msg = "Name must be at least 2 characters."; }
		else if (v.length > 120) { msg = "Name must be 120 characters or fewer."; }
		return setError(f.input, f.err, msg);
	}

	function validateConfirm() {
		var f = field(form.querySelector("[data-confirm]"));
		var pw = form.querySelector("[data-password]").value;
		var v = f.input.value;
		var msg = "";
		if (!v) { msg = "Please confirm your password."; }
		else if (v !== pw) { msg = "Passwords do not match."; }
		return setError(f.input, f.err, msg);
	}

	function validators() {
		var list = [];
		if (isSignup) { list.push(validateName); }
		list.push(validateEmail, validatePassword);
		if (isSignup) { list.push(validateConfirm); }
		return list;
	}

	function validateAll() {
		var ok = true;
		validators().forEach(function (fn) {
			if (!fn()) { ok = false; }
		});
		// DOM order == field order, so the first aria-invalid is the first invalid field
		return { ok: ok, firstInvalid: ok ? null : form.querySelector('[aria-invalid="true"]') };
	}

	/* ---------- real-time behavior: not annoying, per spec ----------
	   - blur → validate that field (user has finished with it)
	   - input → silently clear/update errors on fields already marked
	   - submit → validate everything */
	form.querySelectorAll("[data-email]").forEach(function (el) {
		el.addEventListener("blur", validateEmail);
		el.addEventListener("input", function () {
			if (el.getAttribute("aria-invalid")) { validateEmail(); }
		});
	});
	form.querySelectorAll("[data-password]").forEach(function (el) {
		el.addEventListener("blur", validatePassword);
		el.addEventListener("input", function () {
			if (el.getAttribute("aria-invalid")) { validatePassword(); }
		});
	});
	var nameEl = form.querySelector("[data-name]");
	if (nameEl) {
		nameEl.addEventListener("blur", validateName);
		nameEl.addEventListener("input", function () {
			if (nameEl.getAttribute("aria-invalid")) { validateName(); }
		});
	}
	var confirmEl = form.querySelector("[data-confirm]");
	if (confirmEl) {
		confirmEl.addEventListener("blur", validateConfirm);
		confirmEl.addEventListener("input", function () {
			// confirm must react to password edits too
			validateConfirm();
		});
		var pwEl = form.querySelector("[data-password]");
		pwEl.addEventListener("input", function () {
			if (confirmEl.value) { validateConfirm(); }
		});
	}

	/* ---------- submit: validate, then the REAL backend (Phase 4) ---------- */
	var submitBtn = form.querySelector("[data-submit]");

	function setBusy(busy) {
		if (submitBtn) {
			submitBtn.disabled = busy;
			if (busy) { submitBtn.setAttribute("aria-busy", "true"); }
			else { submitBtn.removeAttribute("aria-busy"); }
		}
	}

	function showComingPanel(message) {
		form.querySelector("[data-password]").value = "";
		if (confirmEl) { confirmEl.value = ""; }
		// reuse the same card: swap the form for the (retitled) panel
		var title = coming.querySelector("h2");
		if (title) { title.textContent = message; }
		var notes = coming.querySelectorAll(".booking-hint");
		notes.forEach(function (p) { p.hidden = true; }); // demo wording is obsolete
		form.hidden = true;
		coming.hidden = false;
		coming.setAttribute("tabindex", "-1");
		coming.focus({ preventScroll: true });
	}

	form.addEventListener("submit", function (e) {
		e.preventDefault();
		var result = validateAll();
		if (!result.ok) {
			announce("Please fix the highlighted fields.");
			if (result.firstInvalid) {
				result.firstInvalid.focus();
			}
			return;
		}
		if (typeof window.AMORE_API === "undefined" || !window.AMORE_API.request) {
			announce("The salon server could not be reached. Please try again.");
			return;
		}

		var email = form.querySelector("[data-email]").value.trim();
		var password = form.querySelector("[data-password]").value;
		setBusy(true);

		if (isSignup) {
			var name = form.querySelector("[data-name]").value.trim();
			window.AMORE_API.signup(name, email, password).then(function (res) {
				if (res.ok) {
					// PHASE 4 requirement 5: signup ESTABLISHES the session — the
					// signup API returns no token, so the same credentials are
					// exchanged for one immediately (api.js stores it), then the
					// user lands where they were headed via ?next=.
					window.AMORE_API.login(email, password).then(function (lres) {
						setBusy(false);
						form.querySelector("[data-password]").value = "";
						if (confirmEl) { confirmEl.value = ""; }
						if (lres.ok) {
							announce("Account created and logged in as " + lres.data.email + ".");
							window.location.replace(nextDestination());
							return;
						}
						// rare (account exists but login hiccup) — honest panel
						showComingPanel("Account created — you're ready to log in");
						announce("Account created for " + res.data.email + ". Please log in.");
					});
					return;
				}
				setBusy(false);
				form.querySelector("[data-password]").value = "";
				if (confirmEl) { confirmEl.value = ""; }
				if (res.status === 409) {
					announce("That email already has an account — log in instead.");
					if (!renderFieldErrors(res.data && res.data.fields)) {
						var f = field(form.querySelector("[data-email]"));
						setError(f.input, f.err, "An account with this email already exists. Try logging in instead.");
					}
					return;
				}
				// 400 validation or anything else — surface honestly
				announce(res.error || "Sign up failed — please try again.");
				if (!renderFieldErrors(res.data && res.data.fields)) {
					var f2 = field(form.querySelector("[data-email]"));
					setError(f2.input, f2.err, res.error || "Sign up failed — please try again.");
				}
			});
			return;
		}

		// LOGIN
		window.AMORE_API.login(email, password).then(function (res) {
			setBusy(false);
			form.querySelector("[data-password]").value = ""; // never keep it around
			if (res.ok) {
				announce("Logged in as " + res.data.email + ".");
				window.location.replace(nextDestination()); // back to the intended flow
				return;
			}
			// 401 (wrong credentials) / network / anything else — honest inline
			announce(res.error || "Log in failed — please try again.");
			var f3 = field(form.querySelector("[data-email]"));
			setError(f3.input, f3.err, res.error || "Log in failed — please try again.");
		});
	});

	/* ---------- password show/hide (one handler serves both pages) ---------- */
	document.querySelectorAll("[data-toggle]").forEach(function (btn) {
		btn.addEventListener("click", function () {
			var input = document.querySelector(btn.getAttribute("data-toggle"));
			if (!input) { return; }
			var show = input.type === "password";
			input.type = show ? "text" : "password";
			btn.setAttribute("aria-pressed", show ? "true" : "false");
			btn.textContent = show ? "Hide" : "Show";
			btn.setAttribute("aria-label", show ? "Hide password" : "Show password");
		});
	});
})();
