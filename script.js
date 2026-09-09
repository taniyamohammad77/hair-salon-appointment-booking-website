/* ==========================================================================
   AURÉLIA — HAIR ATELIER · script.js
   M1 Core Booking: service catalog, staff, availability engine with buffers
   and double-booking prevention, guest booking, confirmation + ICS export.
   ========================================================================== */
"use strict";

/* ---------- Helpers ---------- */
const $  = (sel, ctx = document) => ctx.querySelector(sel);
const $$ = (sel, ctx = document) => [...ctx.querySelectorAll(sel)];

function pad(n) { return String(n).padStart(2, "0"); }

function isoDate(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatDate(dateStr) {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-US", {
    weekday: "short", month: "short", day: "numeric",
  });
}

/* ---------- Catalog (PRD 8: Service / StaffProfile / StaffService) ---------- */
const SERVICES = [
  { id: "cut",        name: "Cut & Styling",        price: 65,  duration: 60,  buffer: 10 },
  { id: "color",      name: "Color & Highlights",   price: 120, duration: 120, buffer: 15 },
  { id: "treatment",  name: "Treatments & Repair",  price: 85,  duration: 75,  buffer: 10 },
  { id: "bridal",     name: "Bridal & Occasion",    price: 150, duration: 90,  buffer: 15 },
  { id: "keratin",    name: "Keratin & Smoothing",  price: 200, duration: 120, buffer: 15 },
  { id: "extensions", name: "Extensions & Volume",  price: 240, duration: 150, buffer: 15 },
];

const STAFF = [
  { id: "ava",    name: "Ava Laurent",   role: "Creative Director" },
  { id: "maya",   name: "Maya Chen",     role: "Color Specialist" },
  { id: "sofia",  name: "Sofia Reyes",   role: "Senior Stylist" },
  { id: "any",    name: "Any available stylist", role: "First available" },
];

const OPEN_HOUR        = 9;    // 9 AM
const CLOSE_HOUR       = 19;   // 7 PM
const SLOT_MIN         = 30;
const MIN_NOTICE_HOURS = 3;    // B-booking rule: minimum notice
const CLOSED_WEEKDAYS  = [0];  // Sunday closed
const STORE_KEY        = "aurelia.bookings.v1";

/* ---------- Cached DOM ---------- */
const header      = $("#header");
const navToggle   = $("#navToggle");
const nav         = $("#nav");
const navLinks    = $$(".nav__link");
const bookingForm = $("#bookingForm");
const timeSlots   = $("#timeSlots");
const slotStatus  = $("#slotStatus");
const dateInput   = $("#bDate");
const serviceSel  = $("#bService");
const stylistSel  = $("#bStylist");
const summary     = $("#summary");
const successBox  = $("#bookingSuccess");
const yearEl      = $("#year");
const toTop       = $("#toTop");
const newsForm    = $("#newsForm");

/* ==========================================================================
   1. BOOKING STORE (localStorage stand-in for the M1 backend)
   ========================================================================== */
const store = {
  read() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY)) || []; }
    catch { return []; }
  },
  write(list) {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(list)); } catch { /* private mode */ }
  },
  all() { return this.read(); },
  add(appt) { const list = this.read(); list.push(appt); this.write(list); },
  hasConflict(staffId, startMin, endMin, dateStr) {
    return this.read().some((a) => {
      if (a.date !== dateStr) return false;
      const staffHit = staffId === "any" || a.staffId === "any" || a.staffId === staffId;
      return staffHit && startMin < a.endMin && endMin > a.startMin;
    });
  },
  upcoming() {
    const now = Date.now();
    return this.read()
      .map((a) => ({ ...a, ts: new Date(`${a.date}T${pad(Math.floor(a.startMin / 60))}:${pad(a.startMin % 60)}:00`).getTime() }))
      .filter((a) => a.ts > now)
      .sort((a, b) => a.ts - b.ts);
  },
};

/* ==========================================================================
   2. MOBILE NAVIGATION
   ========================================================================== */
function setNav(open) {
  nav.classList.toggle("is-open", open);
  navToggle.setAttribute("aria-expanded", String(open));
  navToggle.setAttribute("aria-label", open ? "Close menu" : "Open menu");
  document.body.style.overflow = open ? "hidden" : "";
}

navToggle.addEventListener("click", () => setNav(!nav.classList.contains("is-open")));
nav.addEventListener("click", (e) => { if (e.target.closest("a")) setNav(false); });
nav.addEventListener("keydown", (e) => { if (e.key === "Escape") setNav(false); });

/* ==========================================================================
   3. SCROLL SPY + SMOOTH SCROLL
   ========================================================================== */
const spyLinks = navLinks.filter((l) => l.getAttribute("href").startsWith("#"));
const spySections = spyLinks.map((l) => $(l.getAttribute("href"))).filter(Boolean);

function setActiveLink(id) {
  spyLinks.forEach((l) => l.classList.toggle("is-active", l.getAttribute("href") === `#${id}`));
}

const spy = new IntersectionObserver(
  (entries) => entries.forEach((en) => en.isIntersecting && setActiveLink(en.target.id)),
  { rootMargin: "-45% 0px -50% 0px" }
);
spySections.forEach((s) => spy.observe(s));

document.querySelectorAll('a[href^="#"]').forEach((link) => {
  link.addEventListener("click", (e) => {
    const id = link.getAttribute("href");
    if (id.length < 2) return;
    const target = $(id);
    if (!target) return;
    e.preventDefault();
    const top = target.getBoundingClientRect().top + window.scrollY - header.offsetHeight - 12;
    window.scrollTo({ top, behavior: "smooth" });
    history.replaceState(null, "", id);
  });
});

/* ==========================================================================
   4. SCROLL REVEAL + HEADER SHADOW + BACK-TO-TOP
   ========================================================================== */
const revealer = new IntersectionObserver(
  (entries, obs) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        obs.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
);
$$(".reveal").forEach((el) => revealer.observe(el));

let ticking = false;
window.addEventListener(
  "scroll",
  () => {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      const y = window.scrollY;
      header.classList.toggle("is-scrolled", y > 8);
      toTop.classList.toggle("is-visible", y > 700);
      ticking = false;
    });
  },
  { passive: true }
);

toTop.addEventListener("click", () => window.scrollTo({ top: 0, behavior: "smooth" }));

/* ==========================================================================
   5. TESTIMONIAL SLIDER
   ========================================================================== */
(function initSlider() {
  const track = $("#tTrack");
  const dotsBox = $("#tDots");
  const box = $("#slider");
  if (!track || !dotsBox || !box) return;

  const slides = $$(".review", track);
  const prev = $("#tPrev");
  const next = $("#tNext");
  let index = 0;
  let timer = null;

  const dots = slides.map((_, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "slider__dot";
    b.setAttribute("aria-label", `Go to review ${i + 1}`);
    b.addEventListener("click", () => goTo(i, true));
    dotsBox.appendChild(b);
    return b;
  });

  function goTo(i, user = false) {
    index = (i + slides.length) % slides.length;
    track.style.transform = `translateX(-${index * 100}%)`;
    dots.forEach((d, di) => d.classList.toggle("is-active", di === index));
    if (user) restart();
  }
  const nextFn = () => goTo(index + 1);
  next.addEventListener("click", nextFn);
  prev.addEventListener("click", () => goTo(index - 1));

  function restart() {
    clearInterval(timer);
    timer = setInterval(nextFn, 6500);
  }
  restart();
  box.addEventListener("mouseenter", () => clearInterval(timer));
  box.addEventListener("mouseleave", restart);
  document.addEventListener("visibilitychange", () =>
    document.hidden ? clearInterval(timer) : restart()
  );
})();

/* ==========================================================================
   6. BOOKING ENGINE
   ========================================================================== */

/* Populate service dropdown with duration (B-1) */
SERVICES.forEach((s) => {
  const opt = document.createElement("option");
  opt.value = s.id;
  opt.textContent = `${s.name} · ${s.duration} min · from $${s.price}`;
  serviceSel.appendChild(opt);
});

/* Assign "any" option if markup lists only named stylists */
if (![...stylistSel.options].some((o) => o.value === "any")) {
  const anyOpt = document.createElement("option");
  anyOpt.value = "any";
  anyOpt.textContent = "Any available stylist";
  stylistSel.prepend(anyOpt);
}

/* Date constraints: today → +60 days (max advance booking) */
const today = new Date();
dateInput.min = isoDate(today);
dateInput.max = isoDate(new Date(today.getTime() + 60 * 864e5));

function serviceById(id) { return SERVICES.find((s) => s.id === id); }
function staffName(id) {
  const s = STAFF.find((x) => x.id === id);
  return s ? s.name : "Any available stylist";
}

/* Candidate staff for a slot when "any" is requested (B-2) */
function candidateStaffIds() {
  return stylistSel.value === "any"
    ? STAFF.filter((s) => s.id !== "any").map((s) => s.id)
    : [stylistSel.value];
}

/* Availability engine: working hours, notice, buffers, double-booking (B-3, 5.6) */
function buildSlots(dateStr) {
  timeSlots.innerHTML = "";

  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  if (CLOSED_WEEKDAYS.includes(date.getDay())) {
    slotStatus.textContent = "We're closed on Sundays — please pick another day.";
    updateSummary();
    return;
  }

  let grid = [];
  for (let t = OPEN_HOUR * 60; t < CLOSE_HOUR * 60; t += SLOT_MIN) grid.push(t);

  if (dateStr === isoDate(new Date())) {
    const now = new Date();
    const cutoff = now.getHours() * 60 + now.getMinutes() + MIN_NOTICE_HOURS * 60;
    grid = grid.filter((t) => t > cutoff);
    if (!grid.length) {
      slotStatus.textContent = "No more slots today — please choose another date.";
      updateSummary();
      return;
    }
  }

  /* Deterministic walk-in load so the UI feels alive, stable per date */
  function seededRand(seedStr) {
    let h = 2166136261;
    for (const ch of seedStr) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); }
    return () => {
      h = Math.imul(h ^ (h >>> 15), 2246822519);
      h = Math.imul(h ^ (h >>> 13), 3266489917);
      h = Math.imul(h ^ (h >>> 16), 1);
      return ((h >>> 0) % 1000) / 1000;
    };
  }
  const rnd = seededRand(dateStr);
  const salonBusy = []; // {staffId, startMin, endMin} simulated existing appointments
  grid.forEach((t) => {
    candidateStaffIds().forEach((sid) => {
      if (rnd() < 0.28) salonBusy.push({ staffId: sid, startMin: t, endMin: t + SLOT_MIN + 20 });
    });
  });

  const svc = serviceById(serviceSel.value);
  const need = svc ? svc.duration + svc.buffer : SLOT_MIN + 10;

  let freeCount = 0;
  grid.forEach((startMin) => {
    const endMin = startMin + need;
    if (endMin > CLOSE_HOUR * 60) return; // must finish before closing

    /* Slot is open if ANY candidate stylist is free for the whole span (5.6) */
    const someoneFree = candidateStaffIds().some((sid) => {
      if (store.hasConflict(sid, startMin, endMin, dateStr)) return false;
      return !salonBusy.some(
        (b) => (sid === b.staffId || b.staffId === "any") && startMin < b.endMin && endMin > b.startMin
      );
    });
    if (!someoneFree) return;

    freeCount++;
    const label = `${pad(Math.floor(startMin / 60))}:${pad(startMin % 60)}`;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "slot";
    btn.textContent = label;
    btn.dataset.start = String(startMin);
    btn.addEventListener("click", () => {
      $$(".slot.is-selected", timeSlots).forEach((b) => b.classList.remove("is-selected"));
      btn.classList.add("is-selected");
      setTimeError("");
      updateSummary();
    });
    timeSlots.appendChild(btn);
  });

  slotStatus.textContent = freeCount
    ? `${freeCount} time${freeCount > 1 ? "s" : ""} available on ${formatDate(dateStr)} · durations include setup buffer.`
    : "Fully booked on this date — please try another day.";
  updateSummary();
}

dateInput.addEventListener("change", () => {
  clearInvalid(dateInput, bDateError);
  if (dateInput.value) buildSlots(dateInput.value);
  else slotStatus.textContent = "Choose a date to see available times.";
});

/* Re-render slots when service or stylist changes (duration/staff affect availability) */
[serviceSel, stylistSel].forEach((el) =>
  el.addEventListener("change", () => {
    if (dateInput.value) buildSlots(dateInput.value);
    updateSummary();
  })
);

/* ---------- Summary ---------- */
const sumService  = $("#sumService");
const sumStylist  = $("#sumStylist");
const sumDate     = $("#sumDate");
const sumTime     = $("#sumTime");
const sumPrice    = $("#sumPrice");
const sumDuration = $("#sumDuration");

function selectedTime() {
  const sel = $(".slot.is-selected", timeSlots);
  return sel ? Number(sel.dataset.start) : null;
}

function updateSummary() {
  const svc = serviceById(serviceSel.value);
  sumService.textContent = svc ? svc.name : "—";
  sumStylist.textContent = staffName(stylistSel.value);
  sumDate.textContent = dateInput.value ? formatDate(dateInput.value) : "—";
  const t = selectedTime();
  sumTime.textContent = t != null ? `${pad(Math.floor(t / 60))}:${pad(t % 60)}` : "—";
  sumPrice.textContent = svc ? `$${svc.price}+` : "—";
  sumDuration.textContent = svc
    ? `Approx. ${svc.duration} min + ${svc.buffer} min buffer.`
    : "Duration shown after choosing a service.";
  summary.classList.remove("flash");
  void summary.offsetWidth;
  summary.classList.add("flash");
}

/* Deep links from service/stylist cards */
$$("[data-book-service]").forEach((el) => {
  el.addEventListener("click", () => {
    const svc = el.dataset.bookService;
    if (svc) serviceSel.value = svc;
    const sty = el.dataset.bookStylist;
    if (sty && STAFF.some((s) => s.id === sty)) stylistSel.value = sty;
    updateSummary();
    if (dateInput.value) buildSlots(dateInput.value);
    if (el.tagName === "BUTTON") {
      const top = $("#booking").getBoundingClientRect().top + window.scrollY - header.offsetHeight - 12;
      window.scrollTo({ top, behavior: "smooth" });
    }
  });
});

/* ==========================================================================
   7. VALIDATION + SUBMIT
   ========================================================================== */
const bNameError    = $("#bNameError");
const bPhoneError   = $("#bPhoneError");
const bEmailError   = $("#bEmailError");
const bServiceError = $("#bServiceError");
const bDateError    = $("#bDateError");
const bTimeError    = $("#bTimeError");

function setInvalid(el, errEl, msg) {
  el.classList.add("is-invalid");
  el.setAttribute("aria-invalid", "true");
  if (errEl) errEl.textContent = msg;
}
function clearInvalid(el, errEl) {
  el.classList.remove("is-invalid");
  el.removeAttribute("aria-invalid");
  if (errEl) errEl.textContent = "";
}
function setTimeError(msg) { bTimeError.textContent = msg; }

const RULES = [
  { el: $("#bName"), err: bNameError,
    test: (v) => v.trim().length >= 2, msg: "Please enter your full name." },
  { el: $("#bPhone"), err: bPhoneError,
    test: (v) => /^[\d\s()+-]{7,}$/.test(v.trim()), msg: "Please enter a valid phone number." },
  { el: $("#bEmail"), err: bEmailError,
    test: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim()), msg: "Please enter a valid email address." },
  { el: serviceSel, err: bServiceError,
    test: (v) => !!serviceById(v), msg: "Please choose a service." },
  { el: dateInput, err: bDateError,
    test: (v) => {
      if (!v) return false;
      const day = new Date(v + "T00:00:00").getDay();
      return !CLOSED_WEEKDAYS.includes(day) && v >= dateInput.min;
    },
    msg: "Pick a date from today onward (closed Sundays)." },
];

function validateRule(rule) {
  const ok = rule.test(rule.el.value);
  if (ok) clearInvalid(rule.el, rule.err);
  else setInvalid(rule.el, rule.err, rule.msg);
  return ok;
}

RULES.forEach((rule) => {
  rule.el.addEventListener("change", () => validateRule(rule));
  rule.el.addEventListener("input", () => {
    if (rule.el.classList.contains("is-invalid")) validateRule(rule);
  });
});

bookingForm.addEventListener("submit", (e) => {
  e.preventDefault();

  let firstBad = null;
  RULES.forEach((rule) => { if (!validateRule(rule) && !firstBad) firstBad = rule.el; });

  const startMin = selectedTime();
  if (startMin == null) {
    setTimeError("Please select an available time.");
    if (!firstBad) firstBad = timeSlots;
  } else {
    setTimeError("");
  }

  if (firstBad) {
    firstBad.scrollIntoView({ behavior: "smooth", block: "center" });
    if (firstBad.focus) firstBad.focus({ preventScroll: true });
    return;
  }

  confirmBooking(startMin);
});

/* Guest booking → store → confirmation (B-4, B-5) */
let lastBooking = null;

function confirmBooking(startMin) {
  const svc = serviceById(serviceSel.value);
  const dateStr = dateInput.value;
  const endMin = startMin + svc.duration + svc.buffer;

  /* Final double-booking check (5.6) — state may have changed since render */
  const conflict = candidateStaffIds().some((sid) => store.hasConflict(sid, startMin, endMin, dateStr));
  if (conflict) {
    setTimeError("Sorry — that time was just taken. Please choose another slot.");
    buildSlots(dateStr);
    return;
  }

  const staffId = stylistSel.value;
  const appt = {
    ref: "AUR-" + Math.random().toString(36).slice(2, 7).toUpperCase(),
    name: $("#bName").value.trim(),
    email: $("#bEmail").value.trim(),
    phone: $("#bPhone").value.trim(),
    notes: $("#bNotes").value.trim(),
    serviceId: svc.id, serviceName: svc.name,
    price: svc.price, duration: svc.duration, buffer: svc.buffer,
    staffId,
    date: dateStr,
    startMin, endMin,
    status: "confirmed",
    createdAt: new Date().toISOString(),
  };
  store.add(appt);
  lastBooking = appt;
  renderVisits();
  showSuccess(appt);
}

function showSuccess(appt) {
  $("#bookingRef").textContent = appt.ref;
  $("#sdService").textContent  = appt.serviceName;
  $("#sdStylist").textContent  = staffName(appt.staffId);
  $("#sdWhen").textContent     = `${formatDate(appt.date)} · ${pad(Math.floor(appt.startMin / 60))}:${pad(appt.startMin % 60)} – ${pad(Math.floor(appt.endMin / 60))}:${pad(appt.endMin % 60)}`;
  $("#sdTotal").textContent    = `$${appt.price}+`;

  successBox.hidden = false;
  successBox.classList.remove("is-shown");
  void successBox.offsetWidth;
  successBox.classList.add("is-shown");
  summary.hidden = true;

  bookingForm.reset();
  timeSlots.innerHTML = "";
  slotStatus.textContent = "Choose a date to see available times.";
  $$(".is-invalid", bookingForm).forEach((el) => {
    el.classList.remove("is-invalid");
    el.removeAttribute("aria-invalid");
  });
  serviceSel.selectedIndex = 0;
  stylistSel.value = "any";
  updateSummary();
  successBox.scrollIntoView({ behavior: "smooth", block: "center" });
}

/* Calendar export (.ics) for the confirmation (N-1 client-side stand-in) */
$("#icsBtn").addEventListener("click", () => {
  if (!lastBooking) return;
  const a = lastBooking;
  const dt = (dateStr, mins) => {
    const [y, m, d] = dateStr.split("-").map(Number);
    return `${y}${pad(m)}${pad(d)}T${pad(Math.floor(mins / 60))}${pad(mins % 60)}00`;
  };
  const ics = [
    "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Aurelia Hair Atelier//Booking//EN",
    "BEGIN:VEVENT",
    `UID:${a.ref}@aurelia-salon.com`,
    `DTSTAMP:${new Date().toISOString().replace(/[-:]/g, "").slice(0, 15)}Z`,
    `DTSTART:${dt(a.date, a.startMin)}`,
    `DTEND:${dt(a.date, a.endMin)}`,
    `SUMMARY:${a.serviceName} — Aurélia Hair Atelier`,
    `DESCRIPTION:Stylist: ${staffName(a.staffId)} · Ref: ${a.ref}`,
    "LOCATION:128 Maiden Lane\\, SoHo\\, New York",
    "END:VEVENT", "END:VCALENDAR",
  ].join("\r\n");
  const url = URL.createObjectURL(new Blob([ics], { type: "text/calendar" }));
  const link = Object.assign(document.createElement("a"), { href: url, download: `${a.ref}.ics` });
  link.click();
  URL.revokeObjectURL(url);
});

$("#bookAgain").addEventListener("click", () => {
  successBox.hidden = true;
  successBox.classList.remove("is-shown");
  summary.hidden = false;
  dateInput.value = "";
});

/* ---------- Upcoming visits card (returning-client view) ---------- */
function renderVisits() {
  const list = $("#visitsList");
  if (!list) return;
  const items = store.upcoming();
  if (!items.length) {
    list.innerHTML = '<li class="visits__empty">No upcoming visits yet — book your first appointment above.</li>';
    return;
  }
  list.innerHTML = "";
  items.slice(0, 4).forEach((a) => {
    const li = document.createElement("li");
    li.innerHTML = `
      <span><strong>${a.serviceName}</strong><small>${formatDate(a.date)} · ${pad(Math.floor(a.startMin / 60))}:${pad(a.startMin % 60)} · ${staffName(a.staffId)}</small></span>
      <code class="visits__ref">${a.ref}</code>`;
    list.appendChild(li);
  });
}
renderVisits();

/* ==========================================================================
   8. NEWSLETTER
   ========================================================================== */
newsForm.addEventListener("submit", (e) => {
  e.preventDefault();
  const input = $("#newsEmail");
  const errEl = $("#newsError");
  const ok = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(input.value.trim());
  if (!ok) {
    input.classList.add("is-invalid");
    errEl.style.color = "";
    errEl.textContent = "Please enter a valid email address.";
    return;
  }
  input.classList.remove("is-invalid");
  errEl.style.color = "var(--gold-soft)";
  errEl.textContent = "Welcome to the list — see you in your inbox ✦";
  input.value = "";
});

/* ==========================================================================
   9. MISC
   ========================================================================== */
yearEl.textContent = new Date().getFullYear();
