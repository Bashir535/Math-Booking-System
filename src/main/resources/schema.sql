CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS providers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    display_name VARCHAR(120) NOT NULL,
    bio TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS services (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT '',
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    price NUMERIC(8, 2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS availability_slots (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    service_id BIGINT NOT NULL REFERENCES services(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    CHECK (ends_at > starts_at),
    UNIQUE (id, service_id),
    CONSTRAINT unique_provider_start UNIQUE (provider_id, starts_at),
    CONSTRAINT no_overlapping_provider_slots EXCLUDE USING gist (
        provider_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
);

CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    slot_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL REFERENCES services(id),
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED'
        CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMPTZ,
    FOREIGN KEY (slot_id, service_id) REFERENCES availability_slots(id, service_id),
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_appointment_per_slot
    ON appointments (slot_id) WHERE status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS slots_by_service_and_date
    ON availability_slots (service_id, starts_at);

CREATE INDEX IF NOT EXISTS appointments_by_customer
    ON appointments (customer_id, created_at);
