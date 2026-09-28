/* ==========================================================================
   AUTH UI (frontend-only, Phase 7) — LOGIN + SIGNUP
   --------------------------------------------------------------------------
   UI-only by design (user rule 5): there is NO authentication backend, so
   the submit path runs INLINE VALIDATION and then shows an explicit,
   unmissable coming-soon state. It never authenticates, redirects, creates
   sessions/tokens, or stores ANYTHING — no localStorage, no sessionStorage,
   no cookies, no password persistence of any kind. Password values live
   only in the input fields while the user types and are cleared on submit.
   Validation rules mirror the future backend contract (email ≤160,
   name 2–120) so the real integration can swap this file's submit branch
   for fetch() without touching the markup.
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

	/* ---------- submit: validate, then HONEST coming-soon (never auth) ---------- */
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
		// Valid input — but there is NO auth backend. Honest state only:
		// clear the in-memory password values, swap to the coming-soon panel,
		// store nothing, redirect nowhere.
		form.querySelector("[data-password]").value = "";
		if (confirmEl) { confirmEl.value = ""; }
		form.hidden = true;
		coming.hidden = false;
		coming.setAttribute("tabindex", "-1");
		coming.focus({ preventScroll: true });
		announce(isSignup
			? "Account creation is coming soon. This demo is not connected to an account service yet."
			: "Authentication is coming soon. This demo is not connected to an account service yet.");
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
