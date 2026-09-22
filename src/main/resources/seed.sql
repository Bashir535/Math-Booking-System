INSERT INTO users (username, password_hash, full_name, role)
VALUES
    ('maya.chen', crypt(gen_random_uuid()::text, gen_salt('bf', 10)), 'Maya Chen', 'PROVIDER'),
    ('daniel.reyes', crypt(gen_random_uuid()::text, gen_salt('bf', 10)), 'Daniel Reyes', 'PROVIDER'),
    ('alex.student', crypt(gen_random_uuid()::text, gen_salt('bf', 10)), 'Alex Morgan', 'CUSTOMER')
ON CONFLICT (username) DO NOTHING;

INSERT INTO providers (user_id, display_name, bio)
SELECT id, full_name,
    CASE username
        WHEN 'maya.chen' THEN 'Algebra and calculus tutoring with step-by-step practice.'
        ELSE 'Geometry and algebra tutoring focused on building confidence.'
    END
FROM users WHERE username IN ('maya.chen', 'daniel.reyes')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO services (name, description, duration_minutes, price)
VALUES
    ('Algebra', 'Equations, functions, and problem-solving practice.', 60, 35.00),
    ('Geometry', 'Shapes, proofs, and coordinate geometry.', 60, 35.00),
    ('Calculus', 'Limits, derivatives, and integrals.', 60, 45.00)
ON CONFLICT (name) DO NOTHING;

INSERT INTO availability_slots (provider_id, service_id, starts_at, ends_at)
SELECT p.id, s.id,
    ((CURRENT_TIMESTAMP AT TIME ZONE 'America/Los_Angeles')::date + days.day + sessions.at_time)
        AT TIME ZONE 'America/Los_Angeles',
    ((CURRENT_TIMESTAMP AT TIME ZONE 'America/Los_Angeles')::date + days.day + sessions.at_time
        + make_interval(mins => s.duration_minutes)) AT TIME ZONE 'America/Los_Angeles'
FROM providers p
JOIN users u ON u.id = p.user_id
CROSS JOIN generate_series(1, 7) AS days(day)
JOIN (VALUES
    ('maya.chen', 'Algebra', TIME '10:00'),
    ('maya.chen', 'Calculus', TIME '14:00'),
    ('daniel.reyes', 'Geometry', TIME '11:00'),
    ('daniel.reyes', 'Algebra', TIME '15:00')
) AS sessions(username, service_name, at_time) ON sessions.username = u.username
JOIN services s ON s.name = sessions.service_name
ON CONFLICT DO NOTHING;
