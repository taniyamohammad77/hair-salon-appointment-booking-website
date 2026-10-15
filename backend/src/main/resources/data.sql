-- ==========================================================================
-- SEED DATA — runs on every startup (spring.sql.init.mode=always).
-- Every INSERT is guarded with IF NOT EXISTS -> 100% idempotent, no
-- duplicates ever. Prices/times here are the single source of truth the
-- API returns (rule 10: what we display comes from the DB).
-- ==========================================================================

-- --------------------------------------------------------------------------
-- SERVICES (unisex — everyone welcome)
-- --------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Classic Haircut')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Classic Haircut', N'Tailored cut, wash and finish — for any hair type.', N'Haircut', 45, 10, 25.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Skin Fade')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Skin Fade', N'Precision fade blended to the skin, styled to finish.', N'Haircut', 45, 10, 28.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Beard Trim & Shape')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Beard Trim & Shape', N'Shape-up, outline and hot-towel finish.', N'Beard', 20, 5, 12.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Wash & Blow Dry')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Wash & Blow Dry', N'Shampoo, conditioner and a smooth blow-dry finish.', N'Styling', 30, 5, 18.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Full Colour')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Full Colour', N'All-over colour with gloss. Patch test advised 48h before.', N'Colour', 90, 20, 60.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Roots Touch-Up')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Roots Touch-Up', N'Regrowth coverage only — quick refresh between colours.', N'Colour', 60, 15, 40.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Hair Treatment')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Hair Treatment', N'Deep-repair mask, scalp massage and steam.', N'Treatment', 30, 10, 22.00, 1);

IF NOT EXISTS (SELECT 1 FROM services WHERE name = N'Kids Haircut')
    INSERT INTO services (name, description, category, duration_minutes, buffer_minutes, price, active)
    VALUES (N'Kids Haircut', N'Gentle cut for ages 3–12, grown-ups welcome to stay.', N'Haircut', 30, 5, 15.00, 1);

-- --------------------------------------------------------------------------
-- STYLISTS
-- --------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM stylists WHERE email = N'maya@glowstudio.example')
    INSERT INTO stylists (name, role, bio, avatar_url, phone, email, active)
    VALUES (N'Maya Chen', N'Senior Stylist',
            N'12 years behind the chair. Precision cuts and calm energy — you are in good hands.',
            NULL, N'555-0101', N'maya@glowstudio.example', 1);

IF NOT EXISTS (SELECT 1 FROM stylists WHERE email = N'omar@glowstudio.example')
    INSERT INTO stylists (name, role, bio, avatar_url, phone, email, active)
    VALUES (N'Omar Haddad', N'Barber & Stylist',
            N'Fades, beards and sharp lines. Talks football, never pushes small talk.',
            NULL, N'555-0102', N'omar@glowstudio.example', 1);

IF NOT EXISTS (SELECT 1 FROM stylists WHERE email = N'sofia@glowstudio.example')
    INSERT INTO stylists (name, role, bio, avatar_url, phone, email, active)
    VALUES (N'Sofia Reyes', N'Colour Specialist',
            N'Balayage, vivids and gentle blonde. Will always tell you what your hair can take.',
            NULL, N'555-0103', N'sofia@glowstudio.example', 1);

-- --------------------------------------------------------------------------
-- STYLISTS_SERVICES (who can do what)
-- --------------------------------------------------------------------------
-- Maya: cuts + colour + treatments + kids
INSERT INTO stylists_services (stylist_id, service_id)
SELECT st.id, s.id
FROM stylists st
JOIN services s ON s.name IN (N'Classic Haircut', N'Wash & Blow Dry', N'Full Colour', N'Roots Touch-Up', N'Hair Treatment', N'Kids Haircut')
WHERE st.email = N'maya@glowstudio.example'
  AND NOT EXISTS (SELECT 1 FROM stylists_services ss WHERE ss.stylist_id = st.id AND ss.service_id = s.id);

-- Omar: cuts + beard + kids + blow dry
INSERT INTO stylists_services (stylist_id, service_id)
SELECT st.id, s.id
FROM stylists st
JOIN services s ON s.name IN (N'Classic Haircut', N'Skin Fade', N'Beard Trim & Shape', N'Wash & Blow Dry', N'Kids Haircut')
WHERE st.email = N'omar@glowstudio.example'
  AND NOT EXISTS (SELECT 1 FROM stylists_services ss WHERE ss.stylist_id = st.id AND ss.service_id = s.id);

-- Sofia: colour + treatments + blow dry
INSERT INTO stylists_services (stylist_id, service_id)
SELECT st.id, s.id
FROM stylists st
JOIN services s ON s.name IN (N'Full Colour', N'Roots Touch-Up', N'Hair Treatment', N'Wash & Blow Dry')
WHERE st.email = N'sofia@glowstudio.example'
  AND NOT EXISTS (SELECT 1 FROM stylists_services ss WHERE ss.stylist_id = st.id AND ss.service_id = s.id);

-- --------------------------------------------------------------------------
-- WORKING HOURS (weekday: 1 = Monday ... 7 = Sunday)
-- --------------------------------------------------------------------------
-- Maya: Mon–Fri 09:00–17:00
INSERT INTO working_hours (stylist_id, weekday, start_time, end_time)
SELECT st.id, d.wd, N'09:00', N'17:00'
FROM stylists st
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(wd)
WHERE st.email = N'maya@glowstudio.example'
  AND NOT EXISTS (SELECT 1 FROM working_hours wh WHERE wh.stylist_id = st.id AND wh.weekday = d.wd);

-- Omar: Tue–Sat 10:00–18:00
INSERT INTO working_hours (stylist_id, weekday, start_time, end_time)
SELECT st.id, d.wd, N'10:00', N'18:00'
FROM stylists st
CROSS JOIN (VALUES (2), (3), (4), (5), (6)) AS d(wd)
WHERE st.email = N'omar@glowstudio.example'
  AND NOT EXISTS (SELECT 1 FROM working_hours wh WHERE wh.stylist_id = st.id AND wh.weekday = d.wd);

-- Sofia: Wed–Sun 09:30–17:30
INSERT INTO working_hours (stylist_id, weekday, start_time, end_time)
SELECT st.id, d.wd, N'09:30', N'17:30'
FROM stylists st
CROSS JOIN (VALUES (3), (4), (5), (6), (7)) AS d(wd)
WHERE st.email = N'sofia@glowstudio.example'
  AND NOT EXISTS (SELECT 1 FROM working_hours wh WHERE wh.stylist_id = st.id AND wh.weekday = d.wd);

-- time_off starts empty — add rows via the app/DB when a stylist takes leave.
