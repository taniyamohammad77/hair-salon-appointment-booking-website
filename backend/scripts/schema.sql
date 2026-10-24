/* ==========================================================================
   HAIR SALON BOOKING SYSTEM — DATABASE SCHEMA (Microsoft SQL Server)
   --------------------------------------------------------------------------
   How to run:  open in SSMS (or sqlcmd) connected with Windows
                Authentication and execute (F5). Safe to re-run: every
                statement is guarded with IF NOT EXISTS / IF OBJECT_ID.

   This file IS the database design documentation (for the viva):
   - every table, key and relationship
   - every CHECK constraint (rule 7: wrong input is impossible at DB level)
   - the double-booking unique index (safety net layer 3)
   The application validates again in Java (layer 1+2) — defense in depth.
   ========================================================================== */

/* Required SET options for filtered indexes (sqlcmd/ODBC default is OFF) */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* --------------------------------------------------------------------------
   0. DATABASE — create if missing, then work inside it
   -------------------------------------------------------------------------- */
IF DB_ID(N'SalonBooking') IS NULL
BEGIN
    CREATE DATABASE SalonBooking;
END
GO

USE SalonBooking;
GO

/* --------------------------------------------------------------------------
   1. SERVICES — what can be booked
   duration + buffer are the building blocks of every slot calculation.
   buffer = cleanup / changeover time appended after the service.
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.services', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.services (
        id               BIGINT IDENTITY(1,1) NOT NULL
                         CONSTRAINT PK_services PRIMARY KEY,
        name             NVARCHAR(120)  NOT NULL,
        description      NVARCHAR(1000) NULL,
        category         NVARCHAR(60)   NULL,      -- e.g. Haircut, Colour, Beard, Styling, Treatment
        duration_minutes INT            NOT NULL,
        buffer_minutes   INT            NOT NULL
                         CONSTRAINT DF_services_buffer DEFAULT(0),
        price            DECIMAL(10,2)  NOT NULL,
        active           BIT            NOT NULL
                         CONSTRAINT DF_services_active DEFAULT(1),  -- soft delete

        CONSTRAINT CK_services_duration CHECK (duration_minutes > 0),
        CONSTRAINT CK_services_buffer   CHECK (buffer_minutes >= 0),
        CONSTRAINT CK_services_price    CHECK (price >= 0)
    );
END;
GO

/* --------------------------------------------------------------------------
   2. STYLISTS — who can be booked (unisex: no gender fields)
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.stylists', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stylists (
        id          BIGINT IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_stylists PRIMARY KEY,
        name        NVARCHAR(120)  NOT NULL,
        role        NVARCHAR(80)   NULL,             -- e.g. Senior Stylist
        bio         NVARCHAR(1000) NULL,
        avatar_url  NVARCHAR(500)  NULL,
        phone       NVARCHAR(30)   NULL,
        email       NVARCHAR(160)  NULL,
        active      BIT            NOT NULL
                    CONSTRAINT DF_stylists_active DEFAULT(1),  -- soft delete

        CONSTRAINT UQ_stylists_email UNIQUE (email)
    );
END;
GO

/* --------------------------------------------------------------------------
   3. STYLISTS_SERVICES — M:N junction (composite PK = no duplicate pairs)
   Restrict-delete in both directions: a service/stylist with bookings
   or assignments cannot vanish silently.
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.stylists_services', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.stylists_services (
        stylist_id  BIGINT NOT NULL
                    CONSTRAINT FK_stylists_services_stylist
                    REFERENCES dbo.stylists (id),          -- ON DELETE NO ACTION (restrict)
        service_id  BIGINT NOT NULL
                    CONSTRAINT FK_stylists_services_service
                    REFERENCES dbo.services (id),          -- ON DELETE NO ACTION (restrict)

        CONSTRAINT PK_stylists_services PRIMARY KEY (stylist_id, service_id)
    );
END;
GO

/* --------------------------------------------------------------------------
   4. WORKING_HOURS — weekly shift template, one row per stylist per weekday
   weekday: 1 = Monday ... 7 = Sunday (ISO-8601, matches java DayOfWeek)
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.working_hours', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.working_hours (
        id          BIGINT IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_working_hours PRIMARY KEY,
        stylist_id  BIGINT NOT NULL
                    CONSTRAINT FK_working_hours_stylist
                    REFERENCES dbo.stylists (id) ON DELETE CASCADE,
        weekday     TINYINT NOT NULL,
        start_time  TIME    NOT NULL,
        end_time    TIME    NOT NULL,

        CONSTRAINT CK_wh_weekday CHECK (weekday BETWEEN 1 AND 7),
        CONSTRAINT CK_wh_times   CHECK (end_time > start_time)
    );
END;
GO

/* one shift per stylist per weekday — contradictory duplicate rows impossible */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_wh_stylist_day' AND object_id = OBJECT_ID(N'dbo.working_hours'))
BEGIN
    CREATE UNIQUE INDEX UQ_wh_stylist_day ON dbo.working_hours (stylist_id, weekday);
END;
GO

/* --------------------------------------------------------------------------
   5. TIME_OFF — holidays / leave. NULL start+end = FULL DAY off
   (the availability engine must COALESCE these — see ER-DIAGRAM Trap 2)
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.time_off', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.time_off (
        id          BIGINT IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_time_off PRIMARY KEY,
        stylist_id  BIGINT NOT NULL
                    CONSTRAINT FK_time_off_stylist
                    REFERENCES dbo.stylists (id) ON DELETE CASCADE,
        off_date    DATE   NOT NULL,
        start_time  TIME   NULL,      -- NULL + NULL = full day
        end_time    TIME   NULL,
        reason      NVARCHAR(200) NULL,

        CONSTRAINT CK_to_times CHECK (end_time > start_time)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_time_off_stylist_date' AND object_id = OBJECT_ID(N'dbo.time_off'))
BEGIN
    CREATE INDEX IX_time_off_stylist_date ON dbo.time_off (stylist_id, off_date);
END;
GO

/* --------------------------------------------------------------------------
   6. BOOKINGS — the heart of the system
   status lifecycle: CONFIRMED -> COMPLETED | CANCELLED | NO_SHOW
   end_time is ALWAYS server-computed: booking_time + duration + buffer
   (never user-entered — ER-DIAGRAM Trap 4)
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.bookings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.bookings (
        id            BIGINT IDENTITY(1,1) NOT NULL
                      CONSTRAINT PK_bookings PRIMARY KEY,
        customer_name NVARCHAR(120)  NOT NULL,
        phone         NVARCHAR(30)   NOT NULL,
        email         NVARCHAR(160)  NOT NULL,
        service_id    BIGINT         NOT NULL
                      CONSTRAINT FK_bookings_service
                      REFERENCES dbo.services (id),        -- restrict delete
        stylist_id    BIGINT         NOT NULL
                      CONSTRAINT FK_bookings_stylist
                      REFERENCES dbo.stylists (id),        -- restrict delete
        booking_date  DATE           NOT NULL,
        booking_time  TIME           NOT NULL,
        end_time      TIME           NOT NULL,             -- server-computed
        status        NVARCHAR(20)   NOT NULL
                      CONSTRAINT DF_bookings_status DEFAULT(N'CONFIRMED'),
        notes         NVARCHAR(500)  NULL,
        created_at    DATETIME2      NOT NULL
                      CONSTRAINT DF_bookings_created DEFAULT(SYSUTCDATETIME()),

        CONSTRAINT CK_bk_status CHECK (status IN (N'CONFIRMED', N'COMPLETED', N'CANCELLED', N'NO_SHOW')),
        CONSTRAINT CK_bk_times  CHECK (end_time > booking_time)
    );
END;
GO

/* --------------------------------------------------------------------------
   THE DOUBLE-BOOKING KILLER (safety net layer 3)
   SQL Server physically refuses two CONFIRMED bookings for the same
   stylist at the same start time. Filtered on CONFIRMED so completed /
   cancelled bookings free the slot.
   NOTE: this blocks exact duplicates only — overlap protection lives in
   the Java availability engine + save transaction (Traps 1 & 3).
   -------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_bookings_slot' AND object_id = OBJECT_ID(N'dbo.bookings'))
BEGIN
    CREATE UNIQUE INDEX UQ_bookings_slot
        ON dbo.bookings (stylist_id, booking_date, booking_time)
        WHERE status = N'CONFIRMED';
END;
GO

/* fast lookups for "my appointments" by email */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bookings_email' AND object_id = OBJECT_ID(N'dbo.bookings'))
BEGIN
    CREATE INDEX IX_bookings_email ON dbo.bookings (email, booking_date DESC);
END;
GO

/* --------------------------------------------------------------------------
   7. REVIEWS — at most ONE per booking (booking_id UNIQUE)
   App-layer rule: only allowed when the booking status = COMPLETED.
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.reviews', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.reviews (
        id          BIGINT IDENTITY(1,1) NOT NULL
                    CONSTRAINT PK_reviews PRIMARY KEY,
        booking_id  BIGINT NOT NULL
                    CONSTRAINT FK_reviews_booking
                    REFERENCES dbo.bookings (id) ON DELETE CASCADE,
        rating      TINYINT NOT NULL,
        comment     NVARCHAR(1000) NULL,
        created_at  DATETIME2 NOT NULL
                    CONSTRAINT DF_reviews_created DEFAULT(SYSUTCDATETIME()),

        CONSTRAINT UQ_reviews_booking UNIQUE (booking_id),
        CONSTRAINT CK_rev_rating CHECK (rating BETWEEN 1 AND 5)
    );
END;
GO

/* --------------------------------------------------------------------------
   8. USERS — customer accounts for authentication (AUTH PHASE 1, approved)
   Bookings link to users BY EMAIL only — no FK by design: bookings created
   before accounts existed must keep working. The app layer lowercases
   emails (same rule as BookingService), so with the database's CI
   collation + UQ_users_email, duplicate addresses are impossible
   regardless of case. password_hash holds a BCrypt hash (60 chars);
   NVARCHAR(100) leaves headroom for future hash formats. Password policy
   (minimum 8 characters) is enforced app-side, never here — only the
   hash is stored, so a DB-level length check would be meaningless.
   -------------------------------------------------------------------------- */
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id             BIGINT IDENTITY(1,1) NOT NULL
                       CONSTRAINT PK_users PRIMARY KEY,
        name           NVARCHAR(120)  NOT NULL,
        email          NVARCHAR(160)  NOT NULL,
        password_hash  NVARCHAR(100)  NOT NULL,
        email_verified BIT            NOT NULL
                       CONSTRAINT DF_users_email_verified DEFAULT(0),
        created_at     DATETIME2      NOT NULL
                       CONSTRAINT DF_users_created DEFAULT(SYSUTCDATETIME()),

        /* PHASE 5: automated email verification — random single-use token
           issued at signup, emailed as a link; NULL once verified or when
           no verification is pending (legacy/manual accounts). */
        verification_token NVARCHAR(64) NULL,
        token_expiry_date  DATETIME2    NULL,

        CONSTRAINT UQ_users_email UNIQUE (email)
    );
END;
GO

/* PHASE 5A migration: email_verified gates LEGACY bookings (rows that
   existed before the account did). A brand-new account matching some old
   bookings' email must NOT automatically inherit them — the salon flips
   this flag to 1 after verifying the person (phone / in person). Bookings
   created by the account itself (created_at >= account created_at) are
   always visible, verified or not. Guarded: safe to re-run. */
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users') AND name = N'email_verified')
BEGIN
    ALTER TABLE dbo.users
        ADD email_verified BIT NOT NULL
        CONSTRAINT DF_users_email_verified DEFAULT(0);
END;
GO

/* PHASE 5 migration: automated email verification replaces the manual
   flag flip. A CSPRNG token (64 hex chars) is stored per unverified
   signup with an expiry; GET /api/auth/verify consumes it and sets
   email_verified = 1. Nullable: verified / legacy accounts have no token.
   Guarded: safe to re-run. */
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users') AND name = N'verification_token')
BEGIN
    ALTER TABLE dbo.users
        ADD verification_token NVARCHAR(64) NULL;
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.users') AND name = N'token_expiry_date')
BEGIN
    ALTER TABLE dbo.users
        ADD token_expiry_date DATETIME2 NULL;
END;
GO

/* --------------------------------------------------------------------------
   9. COLUMN COMMENTS (extended properties) — self-documenting DB
   -------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.services') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'Bookable salon services. Soft-deleted via active = 0.', @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'services';

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.bookings') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'Appointments. end_time computed by the backend (duration + buffer). Double-booking prevented by UQ_bookings_slot + app-layer overlap check.', @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'bookings';

IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE major_id = OBJECT_ID(N'dbo.users') AND minor_id = 0 AND name = N'MS_Description')
    EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'Customer accounts for authentication (auth Phase 1). Bookings link by email — no FK by design. password_hash = BCrypt; password policy (min 8 chars) is app-layer. email_verified gates legacy (pre-account) bookings; Phase 5 sets it via the emailed verification token (verification_token + token_expiry_date, cleared on use).', @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE', @level1name = N'users';
GO

PRINT N'SalonBooking schema ready.';
