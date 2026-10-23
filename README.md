# Amoré — Hair Salon Appointment Booking System

A unisex salon booking site: customers browse services and stylists, pick a
free slot, and manage their own appointments (view / reschedule / cancel).
There is no admin UI — the salon manages verification and data directly in
the database.

```
Static frontend (HTML/CSS/JS)  →  REST API (Spring Boot)  →  JPA/Hibernate  →  SQL Server
   http://localhost:8081            http://localhost:8080                    localhost:1433
```

## Status: local development ready — NOT a production deployment

Verified working locally: booking flow, authentication, booking ownership,
the legacy-booking visibility gate (live-audited), **per-IP rate limiting**
on login/signup/booking writes (30/10/30 per minute, trusted-proxy XFF guard,
`Retry-After` on 429 — unit-verified), and the **reviews API — fully
implemented and verified** (public `GET /api/reviews` + authenticated
`POST /api/reviews` over the hybrid completion rule: a visit is reviewable
when it is CONFIRMED and has already ended, or was marked COMPLETED. The
homepage renders real reviews with an honest empty state; My Visits posts
them. Full suite: 84/84 green, 0 failures). **Not implemented — do
not assume:** automated email-verification flow
(the salon flips `users.email_verified` manually), HTTPS, production hosting,
GitHub Pages serving.

## Stack

- **Backend:** Java 21, Spring Boot, Maven wrapper (no global Maven needed)
- **Frontend:** plain HTML/CSS/JS — no build step
- **Database:** Microsoft SQL Server, **Windows Authentication** (no DB passwords anywhere)

## Prerequisites

- Java 21 (`java -version`)
- Node.js (only for the local frontend dev server)
- A local SQL Server reachable at `localhost:1433` (tested: SQL Server 2012)

## One-time database setup

Run `backend/scripts/schema.sql` in SSMS or sqlcmd (Windows auth). It is
idempotent and fully commented — it creates every table including `dbo.users`
(auth) with the `email_verified` legacy-gate column, and it **is** the schema
documentation. Safe to re-run.

## One-time secret setup — `AMORE_TOKEN_SECRET`

Bearer tokens are signed with an HMAC-SHA256 key that **must never be
committed**. `application.yml` reads it as `${AMORE_TOKEN_SECRET:}` and the
app **fails fast at startup** if the variable is missing or shorter than 32
characters — there is no default and no fallback.

**1. Generate** (Git Bash — 32 random bytes, stored *outside* the repo):

```bash
mkdir -p ~/.amore && openssl rand -hex 32 > ~/.amore/token-secret.txt && chmod 600 ~/.amore/token-secret.txt
```

**2. Set the user-level environment variable** (PowerShell reads the file, so
the value never appears on a command line or in shell history):

```powershell
[Environment]::SetEnvironmentVariable('AMORE_TOKEN_SECRET', (Get-Content -Raw "$env:USERPROFILE\.amore\token-secret.txt").Trim(), 'User')
```

**3. Verify** (prints the length only — never the value):

```powershell
[Environment]::GetEnvironmentVariable('AMORE_TOKEN_SECRET','User').Length   # expect 64
```

Open a **new** terminal afterwards — already-running processes keep the old
environment. Never paste the value into Git, logs, chat, or source files.
Rotating the key invalidates all existing sessions (users simply log in again).

## Run it locally

**Backend** (from `backend/`): `./run-dev.sh` (Git Bash) or `run-dev.cmd`
(cmd/PowerShell). First run builds the jar and downloads the Windows-auth DLL
into `backend/lib/`. Seed data (`data.sql`) loads automatically and is
idempotent. Check: `curl http://localhost:8080/api/health` → `{"status":"up"}`.

**Frontend:** double-click `start-frontend.cmd` (or `npm run dev`) →
http://localhost:8081. Never open the HTML via `file://` — the browser's CORS
rule blocks API calls from file origins (by design); the page then shows an
honest fallback banner.

> **Why the odd JVM flags?** The local SQL Server 2012 predates TLS 1.2, so
> the dev launcher re-enables legacy TLS via `dev-tls12-legacy.security`
> (dev-only, loopback only).

## API

All JSON under `/api`.

### Public — no token required

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | liveness |
| GET | `/services` · `/services/{id}` | service catalogue |
| GET | `/stylists?serviceId=` | stylists, optionally per service |
| GET | `/availability?serviceId=&stylistId=&date=` | free start times (all three params required) |
| GET | `/reviews?limit=` | published reviews, newest first (`limit` clamped 1–50, default 12); guest names are masked server-side |

### Authentication

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/signup` | create account (password min 8, BCrypt-hashed) |
| POST | `/auth/login` | returns a 7-day HMAC-SHA256 bearer token |
| GET | `/auth/me` | revalidate the current session |

Send the token as `Authorization: Bearer <token>`. The frontend keeps it in
`sessionStorage` and revalidates via `/me` on page loads. Login and signup
errors are honest 401/400 responses; passwords are never echoed or logged.

### Authenticated — bearer token required

| Method | Path | Purpose |
|---|---|---|
| POST | `/bookings` | create a booking (**login required**) |
| GET | `/bookings/{id}` | booking details — **owner only** |
| GET | `/bookings?email=` | "My Visits" — **own email only** |
| PUT | `/bookings/{id}/cancel` | cancel — **owner only** |
| PUT | `/bookings/{id}/reschedule` | move — **owner only** (body: `date`, `time`) |
| POST | `/reviews` | review your **own** finished booking — **owner only** (body: `bookingId`, `rating` 1–5, optional `comment` ≤1000 chars) |

**Authorization behavior (live-audited):**

- **401** — missing/invalid/expired token, or bad credentials at login.
- **403** — a logged-in user requesting someone else's booking. The message
  is deliberately generic: no booking data and no existence leak.
- **Legacy gate (Phase 5A):** bookings created *before* an account existed
  are hidden from **unverified** accounts — they're filtered from the list,
  and direct get/cancel/reschedule returns the same generic 403. The salon
  flips `users.email_verified = 1` manually after verifying the person. A
  user's own post-signup bookings are always visible.

**Errors:** 400 validation (+ `fields`), 404 unknown id, 409 slot already
taken (frontend reloads slots) / review not open yet / booking already
reviewed, 429 rate-limited writes (with a standard
`Retry-After` header) — always clean `{"error": "friendly message"}`.

## How double-booking is impossible (3 layers)

1. **Availability engine** — shifts − time-off − confirmed bookings, sliced
   by duration + buffer, with interval-overlap math (`s1 < e2 && s2 < e1`).
2. **Save transaction** — pessimistic-lock re-check inside the transaction;
   the loser gets HTTP 409.
3. **Database** — filtered unique index `UQ_bookings_slot(stylist, date, time)
   WHERE status='CONFIRMED'` physically refuses duplicates.

`end_time` is always computed server-side (`start + duration + buffer`) —
the request DTO has no such field, so it can never be injected.

## Testing

```bash
cd backend

# 1. compile main + test sources (offline, no DB):
./mvnw -o test-compile

# 2. unit tests (no database, no Spring context, no env secret needed — also
#    what CI runs on every push/PR):
./mvnw -o test -Dtest='TokenServiceTest,AvailabilityServiceSlotMathTest,BookingServiceRaceTest,RateLimitServiceTest,RateLimitInterceptorTest,RateLimitEndpointScopingTest,GlobalExceptionHandlerTest'

# 3. full suite (needs local SQL Server AND AMORE_TOKEN_SECRET set):
./mvnw -o test
```

The integration tests commit fixture accounts/bookings (emails at
`example.com`) and remove them in `@AfterAll`; `LegacyBookingGateTest` also
self-heals leftovers from an aborted run. They run against whatever database
`spring.datasource.url` points at — **use a dedicated test database, not the
shared one**:

```bash
# one-time: create the isolated DB, then run a COPY of schema.sql whose
# "USE SalonBooking;" line is changed to "USE SalonBooking_Test;"
./mvnw -o test -Dspring.datasource.url='jdbc:sqlserver://localhost:1433;databaseName=SalonBooking_Test;encrypt=false;integratedSecurity=true;'
```

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "Cannot reach the salon server" banner | Backend not running — start it, check `/api/health` |
| Startup fails: `salon.auth.token-secret (environment variable AMORE_TOKEN_SECRET) must be set…` | Set the env var (above) and open a **new** terminal |
| Slots show "(sample times)" | Availability API unreachable → honest sample fallback; re-pick the slot to retry |
| First availability request is slow | Cold SQL connection pool; retry recovers |
| `TLSv1` / `HHH000511` warnings in logs | Benign — local SQL Server 2012 negotiates TLS 1.0; handled by `dev-tls12-legacy.security` |
| DB login/connect errors | Is SQL Server running on `localhost:1433` with Windows auth? |

## Project layout

```
frontend/            ← the site (8 HTML pages, css/, js/) — served at :8081
backend/
├── scripts/schema.sql          ← documented DB schema (run once)
├── src/main/java/com/salon/
│   ├── controller/  AuthController (/api/auth) · PublicController (everything else)
│   ├── service/     Auth · Token · Availability · Booking · Catalogue
│   ├── repository/  entity/  dto/  config/  exception/
├── src/main/resources/
│   ├── application.yml         ← DB connection + ${AMORE_TOKEN_SECRET:}
│   └── data.sql                ← idempotent seed (services/stylists/shifts)
├── src/test/java/com/salon/    ← 84 tests (unit + integration)
├── run-dev.sh / run-dev.cmd    ← start with the correct JVM flags
└── dev-tls12-legacy.security   ← dev-only TLS override
start-frontend.cmd · serve-frontend.js · package.json   ← frontend dev server (:8081)
```

## Local development vs production

What works today is **localhost only**: Windows-auth DB on 127.0.0.1, CORS
restricted to `localhost:*`, no HTTPS, secret from a local environment
variable. A production launch would additionally require: a remote DB with
`encrypt=true` + real credentials, HTTPS/TLS termination, the secret in a
proper secret store, a shared rate-limit store for multiple instances
(currently in-memory, single instance), an email-verification flow, and a
hosting decision — **none of those exist yet**.

Design references: `ER-DIAGRAM.md` (schema + constraints + traps),
`PHASE-1-DESIGN-PLAN.md` (phase plan).
