# PHASE 1 — VISUAL & UX DESIGN PLAN (v2 · ✅ FOUNDATION APPROVED Sept 21, 2026)

> APPROVAL STATUS: the user approved the Phase 1 FOUNDATION BUILD
> (frontend/index.html + css/style.css + js/main.js, placeholder branding)
> on Sept 21, 2026 — "approved like it".
> STILL OPEN (require explicit user decisions): brand name, logo, tagline,
> exact accent value (#E11D48 vs #C8102E), font pairing, final imagery/copy.
> Phase 2 may now be planned but its scope must be agreed with the user first.

---

## BINDING WORKING RULES (from the user)

1. Build in clearly separated phases; every phase needs explicit user approval.
2. Spring Boot backend exists and works — do NOT connect the frontend to it yet.
   Integration is the FINAL phase after UI/UX is fully approved.
3. Stack: HTML · CSS · Vanilla JS. No React, no frameworks. Lightweight, clear
   file organization.
4. The user personally approves: brand name, logo, tagline, colors, fonts,
   header, hero, sections, booking UX, animation style. Nothing is final until
   approved.
5. Login/Signup remains UI-only until a real authentication backend exists.
6. Temporary sample data is allowed for development only and must be clearly
   separated from the future API integration.
7. Gallery/Looks is OPTIONAL, not mandatory.
8. Design direction locked by user: white background · pink/red accent · clean
   unisex · simple premium · mobile + desktop responsive · smooth scrolling ·
   subtle animations · clear booking flow.

---

## 1. BRAND NAME — ⬜ OPEN

| Option | Character |
|---|---|
| Amoré | warm, romantic, unisex (current placeholder — NOT decided) |
| Verve Studio | energetic, modern, neutral |
| Cutt | minimal, one-word |
| (user's own name) | — |

Decision: ⬜ pending

## 2. LOGO CONCEPT — ⬜ OPEN

| Option | Description |
|---|---|
| Scissors-A monogram | two abstract scissor strokes forming an "A"; scales down to favicon |
| Wordmark + accent slash | name in the display font with one accent-colored slash |
| Wordmark only | pure typography, most minimal |

Decision: ⬜ pending

## 3. TAGLINE — ⬜ OPEN

1. "Good hair, every day."
2. "Precision cuts. Zero attitude."
3. "Walk in ordinary. Walk out sharp."
4. "One salon. Every style."
5. "Booked in a minute. Sharp for weeks."

Decision: ⬜ pending

## 4. COLORS — ⬜ OPEN (proposed starting point, not chosen)

| Token | Value | Role |
|---|---|---|
| Background | `#FFFFFF` | main background |
| Surface | `#F6F6F6` | alternate sections, wells |
| Ink | `#161616` | headlines/text |
| Ink-soft | `#6F6A66` | secondary text |
| Line | `#E9E7E5` | borders/dividers |
| Accent | `#E11D48` | buttons, links, highlights (red-pink) |
| Accent-dark | `#BE123C` | hover/pressed |
| Accent-tint | `#FDECEF` | chips, soft washes (sparing) |
| Footer | `#141414` | dark footer, white text |

Open question for the user: red-pink accent (`#E11D48`) vs true red (`#C8102E`).
"Not overly feminine" guard: accent on actions only, never large surfaces.

Decision: ⬜ pending

## 5. FONTS — ⬜ OPEN

| Pairing | Display | Body | Feel |
|---|---|---|---|
| A | Outfit | Inter | modern, neutral |
| B | Space Grotesk | Inter | more edge/character |

Decision: ⬜ pending

## 6. HEADER / NAV — ⬜ OPEN

Sticky white bar; blur + hairline bottom border once scrolled.
Links: Services · Stylists · Reviews · My Visits · Log in (text) · Book now (accent pill).
Mobile: hamburger → slide-down panel, Book now pinned at panel bottom.

Decision: ⬜ pending

## 7. HERO — ⬜ OPEN (revised: no fake availability card)

- Left: eyebrow ("UNISEX SALON"), headline with one accent-colored word,
  sub-line, CTAs: "Book appointment" (solid) + "Explore services" (ghost).
- Right: two offset rounded photo cards. NO "next free slot" card or any
  element that pretends live data — real availability comes from the backend
  in the integration phase.
- Mobile: stacked; headline + one CTA visible without scrolling.

Decision: ⬜ pending

## 8. SECTIONS — ⬜ OPEN (Gallery optional per user)

1 Header · 2 Hero · 3 Trust strip · 4 Services · 5 How it works ·
6 Stylists · 7 Gallery/Looks (OPTIONAL — user includes or drops) · 8 Reviews ·
9 CTA banner · 10 Footer (dark)

Separate flows, not home sections: Booking · My Visits · Login/Signup (UI-only) ·
Cancel/Reschedule.

Decision: ⬜ pending (including Gallery in/out)

## 9. BOOKING UX — ⬜ OPEN

Service → Stylist → Date & time → Details → Review → Confirm ✓
- Progress indicator; "Edit" links on the review step.
- Calendar (past dates disabled) + slot grid fed by DEV-ONLY sample data.
- Pre-designed states: loading skeletons, empty day, conflict message (maps to
  the real HTTP 409 later), inline validation errors.
- Success screen: reference number, summary, "View in My Visits".

Decision: ⬜ pending

## 10. SAMPLE DATA POLICY (user rule 6)

- All development sample data lives in ONE clearly separated module
  (e.g. `frontend/js/sample-data.js`) exposing the SAME data shapes the future
  API will return. No other file may hardcode data.
- Integration phase = replace that single module with real `fetch()` calls.
- Sample data is visibly marked as sample in comments; nothing fake is ever
  presented as live availability.

## 11. LOGIN / SIGNUP (user rule 5)

UI screens only: visual states + validation UX. No fake success. "Coming soon"
behavior until a real auth backend exists and the user approves wiring it.

## 12. ANIMATION STYLE — ⬜ OPEN

- Scroll reveal: fade + 8px rise, plays once.
- Hover: 150–200ms ease-out, subtle lift.
- Smooth anchor scrolling; skeleton shimmer; success check draw.
- `prefers-reduced-motion` respected. No parallax, bounce, or confetti.

Decision: ⬜ pending

---

## APPROVAL CHECKLIST (user signs off each item)

✅ Foundation build (structure, tokens, base UI, animations) — APPROVED Sept 21
⬜ Brand name · ⬜ Logo · ⬜ Tagline · ⬜ Colors (exact accent) · ⬜ Fonts ·
✅ Header concept · ✅ Hero concept (no fake data) · ✅ Section set (Gallery optional) ·
⬜ Booking UX detail · ✅ Animation style
