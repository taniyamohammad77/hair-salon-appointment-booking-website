# ER DIAGRAM — Hair Salon Booking System
### Built: September 11, 2026 · Unisex salon · Public booking (no admin)
### Updated: October 15, 2026 — added `dbo.users` + `email_verified` (auth / Phase 5A legacy gate)

> **How to view:** paste the Mermaid block into [mermaid.live](https://mermaid.live), or open this file on GitHub / VS Code (with a Mermaid extension) — it renders automatically.

---

## 1. ENTITY-RELATIONSHIP DIAGRAM

```mermaid
erDiagram
    SERVICES ||--o{ STYLISTS_SERVICES : "is offered via"
    STYLISTS ||--o{ STYLISTS_SERVICES : "can perform via"
    STYLISTS ||--o{ WORKING_HOURS : "works in"
    STYLISTS ||--o{ TIME_OFF : "takes"
    STYLISTS ||--o{ BOOKINGS : "is booked for"
    SERVICES ||--o{ BOOKINGS : "is booked as"
    BOOKINGS ||--|| REVIEWS : "may receive"
    USERS ||--o{ BOOKINGS : "email match (NO FK — app-layer gate)"

    SERVICES {
        bigint id PK "IDENTITY"
        varchar name "NOT NULL"
        varchar description
        varchar category
        int duration_minutes "NOT NULL, > 0"
        int buffer_minutes "NOT NULL, default 0"
        decimal price "NOT NULL"
        bit active "default 1"
    }

    STYLISTS {
        bigint id PK "IDENTITY"
        varchar name "NOT NULL"
        varchar role "e.g. Senior Stylist"
        varchar bio
        varchar avatar_url
        varchar phone
        varchar email
        bit active "default 1"
    }

    STYLISTS_SERVICES {
        bigint stylist_id PK "FK to stylists.id"
        bigint service_id PK "FK to services.id"
    }

    WORKING_HOURS {
        bigint id PK "IDENTITY"
        bigint stylist_id FK
        tinyint weekday "1=Mon .. 7=Sun"
        time start_time
        time end_time "must be > start_time"
    }

    TIME_OFF {
        bigint id PK "IDENTITY"
        bigint stylist_id FK
        date off_date
        time start_time "NULL = full day off"
        time end_time "NULL = full day off"
        varchar reason
    }

    BOOKINGS {
        bigint id PK "IDENTITY = Booking ID shown to customer"
        varchar customer_name "NOT NULL"
        varchar phone "NOT NULL"
        varchar email "NOT NULL"
        bigint service_id FK
        bigint stylist_id FK
        date booking_date "NOT NULL"
        time booking_time "NOT NULL"
        time end_time "computed = start + duration + buffer"
        varchar status "CONFIRMED/COMPLETED/CANCELLED/NO_SHOW"
        varchar notes
        datetime created_at "default now()"
    }    REVIEWS {
        bigint id PK "IDENTITY"
        bigint booking_id FK "UNIQUE - one review per booking"
        tinyint rating "1..5"
        varchar comment
        datetime created_at
    }

    USERS {
        bigint id PK "IDENTITY"
        varchar name "NOT NULL"
        varchar email "UNIQUE - NOT NULL"
        varchar password_hash "BCrypt (60 chars) - never plaintext"
        bit email_verified "default 0 - Phase 5A legacy gate"
        datetime created_at "default UTC now"
    }
    ```

---

## 2. RELATIONSHIPS (in words — say this in the viva)

| Relationship | Cardinality | Meaning |
|---|---|---|
| STYLISTS ↔ SERVICES | **M : N** | A stylist can perform many services; a service can be performed by many stylists. Resolved by the `stylists_services` junction table (composite PK). |
| STYLISTS → WORKING_HOURS | **1 : N** | Each stylist has one working-hours row per weekday (Mon–Sun). |
| STYLISTS → TIME_OFF | **1 : N** | A stylist can have many days/half-days off (holidays, leave). |
| STYLISTS → BOOKINGS | **1 : N** | A stylist handles many bookings over time. |
| SERVICES → BOOKINGS | **1 : N** | A service can appear in many bookings; each booking is for exactly one service. |
| BOOKINGS → REVIEWS | **1 : 0..1** | A booking can have **at most one** review (`booking_id` is UNIQUE). Review allowed only when status = COMPLETED (checked in the app layer). |
| USERS → BOOKINGS | **1 : N (logical)** | Matched by `email` string — **deliberately NO FK** (schema.sql §8 comment). `email_verified` gates legacy (pre-account) bookings: an unverified account sees only bookings created at/after the account; the salon flips the flag manually after verifying the person. |

**Crow's foot notation:** `||` = exactly one · `o|` = zero or one · `}o` = zero or many · `}|` = one or many

---

## 3. KEYS & DATA INTEGRITY CONSTRAINTS

### Primary keys
Every entity has a surrogate PK (`id`, BIGINT IDENTITY). Exception: `stylists_services` has a **composite PK** (stylist_id, service_id) — this *itself* prevents duplicate pairs.

### Foreign keys
| FK column | References | Rule |
|---|---|---|
| stylists_services.stylist_id | stylists.id | Restrict delete |
| stylists_services.service_id | services.id | Restrict delete |
| working_hours.stylist_id | stylists.id | CASCADE delete (shifts die with stylist) |
| time_off.stylist_id | stylists.id | CASCADE delete |
| bookings.service_id | services.id | Restrict delete |
| bookings.stylist_id | stylists.id | Restrict delete |
| reviews.booking_id | bookings.id | CASCADE delete |

### CHECK constraints (wrong input is impossible at DB level — rule 7)
```sql
-- services
ALTER TABLE services ADD CONSTRAINT CK_services_duration CHECK (duration_minutes > 0);
ALTER TABLE services ADD CONSTRAINT CK_services_buffer   CHECK (buffer_minutes >= 0);
ALTER TABLE services ADD CONSTRAINT CK_services_price    CHECK (price >= 0);

-- working_hours
ALTER TABLE working_hours ADD CONSTRAINT CK_wh_weekday CHECK (weekday BETWEEN 1 AND 7);
ALTER TABLE working_hours ADD CONSTRAINT CK_wh_times   CHECK (end_time > start_time);
-- one shift per stylist per weekday (prevents contradictory duplicate rows)
CREATE UNIQUE INDEX UQ_wh_stylist_day ON working_hours(stylist_id, weekday);

-- time_off
ALTER TABLE time_off ADD CONSTRAINT CK_to_times CHECK (end_time > start_time);

-- bookings
ALTER TABLE bookings ADD CONSTRAINT CK_bk_status CHECK
    (status IN ('CONFIRMED','COMPLETED','CANCELLED','NO_SHOW'));
ALTER TABLE bookings ADD CONSTRAINT CK_bk_times CHECK (end_time > booking_time);

-- reviews
ALTER TABLE reviews ADD CONSTRAINT CK_rev_rating CHECK (rating BETWEEN 1 AND 5);
```

### The double-booking killer (3-layer safety net, layer 3)
```sql
CREATE UNIQUE INDEX UQ_bookings_slot
    ON bookings(stylist_id, booking_date, booking_time)
    WHERE status = 'CONFIRMED';
```
Even if the app has a bug, SQL Server **physically refuses** two CONFIRMED bookings for the same stylist at the same time. (Filtered index = standard SQL Server feature.)

---

## 4. BUSINESS RULES THE DIAGRAM ENFORCES

1. **A booking is always service + stylist + date + time** — never a vague "appointment" (all four FKs/fields NOT NULL).
2. **Only CONFIRMED bookings block slots** — COMPLETED/CANCELLED/NO_SHOW free the slot (that's why the unique index is filtered on status).
3. **end_time is computed, never user-entered** = `booking_time + service.duration_minutes + service.buffer_minutes` (buffer = cleanup/changeover time between customers).
4. **A review can only exist with a completed booking** — one review per booking max, so ratings can't be spammed.
5. **Unisex by design** — services/stylists have no gender fields; `category` is service-type (e.g. Haircut, Colour, Beard, Styling), open to everyone.
6. **Soft delete via `active`** — services/stylists are deactivated, not deleted, so old bookings keep their history intact (no broken FKs, no false data — rule 10).
7. **Auth required to book or manage** — creating a booking and all My-Visits operations (lookup / cancel / reschedule) require a logged-in account (401 otherwise) and are owner-only (403 for someone else's booking). `dbo.users` stores BCrypt hashes only; `email_verified` implements the Phase 5A legacy gate (unverified accounts cannot see pre-account bookings through list, detail, cancel, or reschedule).

---

## 5. HOW AVAILABILITY IS DERIVED (the ER diagram in action)

```
free slots(stylist, date) =
      working_hours rows for (stylist, weekday(date))      -- when they CAN work
    MINUS time_off overlapping that date                   -- when they CAN'T
    MINUS existing CONFIRMED bookings that day             -- already taken
    sliced by service.duration_minutes + buffer_minutes    -- what FITS
```
All four ingredients are exactly the four entities hanging off STYLISTS in the diagram — the schema was designed so this query is possible in one round trip.

---

## 6. IMPLEMENTATION-TIME TRAPS (MUST handle explicitly — flagged by user)

### Trap 1: Overlap ≠ duplicate — the unique index is NOT enough
The unique index `UQ_bookings_slot(stylist_id, booking_date, booking_time)` only blocks bookings with the **exact same start time**. It does **NOT** block overlaps:

```
Existing:  Haircut      10:00 ──────── 11:00
New:       Colour            10:30 ──────── 12:00
                                        ↑ different start time → index allows it → DOUBLE-BOOKED!
```

**Required at implementation (Phase 2 & 3):** interval overlap check against all CONFIRMED bookings for that stylist+date:
```sql
-- two intervals [s1,e1) and [s2,e2) overlap  ⇔  s1 < e2 AND s2 < e1
SELECT 1 FROM bookings b
WHERE b.stylist_id = :stylistId
  AND b.booking_date = :date
  AND b.status = 'CONFIRMED'
  AND b.booking_time < :newEnd          -- existing starts before new ends
  AND b.end_time > :newStart            -- existing ends after new starts
```
`end_time` used here must already include duration + buffer. This check runs in the availability engine (to hide slots) AND again inside the save transaction (defense in depth).

### Trap 2: NULL times in TIME_OFF = full day, not "ignore this row"
A TIME_OFF row with `start_time = NULL AND end_time = NULL` means **full-day off**. A naive overlap query skips NULL rows and offers slots on the stylist's day off → false information (rule 10).

**Required at implementation:** normalize NULLs before comparing:
```sql
WHERE t.off_date = :date
  AND COALESCE(t.start_time, '00:00') < :slotEnd
  AND COALESCE(t.end_time,  '23:59:59') > :slotStart
```
Full-day row → `00:00–23:59:59` → overlaps every possible slot → stylist correctly shows zero availability.

### Trap 3 (bonus): the race condition
Two customers submit the same slot at the same instant — both pass the SELECT check. **Fix:** perform the re-check + INSERT inside one serializable transaction (`UPDLOCK, HOLDLOCK` on the stylist's bookings for that date); the unique index catches anything that still slips through, and the loser gets HTTP 409 → frontend reloads slots. Result: exactly ONE booking is accepted, the other is rejected — never two.

### Trap 4: end_time is NEVER taken from the user (rule 7)
`end_time = booking_time + service.duration_minutes + service.buffer_minutes` — computed by the **backend only**:

- **DTO level:** `BookingRequest` contains NO `endTime` field at all — even a crafted POST cannot inject one (Jackson ignores unknown fields, and there's nothing to bind to)
- **Service level:** computed inside `BookingService` from the service row fetched from the DB (never from client-supplied duration — client sends only `serviceId`)
- **Persisted** on the bookings row so the overlap query (Trap 1) and availability engine (Section 5) read it without recomputation

```
Client sends:  { serviceId: 3, stylistId: 1, date: "2026-09-20", time: "10:00", name, phone, email }
Backend loads: service 3 → duration 45, buffer 15
Backend stores: booking_time 10:00, end_time 11:00   ← server-computed
```

---

## 7. IMPLEMENTATION CHECKLIST (verify at Phase 2/3 — all four traps)

- [ ] **Trap 1:** overlap check `existing.start < newEnd AND existing.end > newStart` in availability engine AND save transaction
- [ ] **Trap 2:** TIME_OFF NULLs normalized with COALESCE → full-day row blocks all slots
- [ ] **Trap 3:** re-check + INSERT in one serializable transaction; unique index as backstop; loser gets 409 → slots reload
- [ ] **Trap 4:** `endTime` absent from BookingRequest DTO; computed in BookingService from DB duration+buffer; tested with a hand-crafted request
