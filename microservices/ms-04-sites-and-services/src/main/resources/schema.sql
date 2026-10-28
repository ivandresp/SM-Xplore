CREATE TABLE IF NOT EXISTS zones (
    zone_id UUID PRIMARY KEY,
    manager_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(30) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS zones_manager_idx ON zones (manager_id);
CREATE INDEX IF NOT EXISTS zones_status_idx ON zones (status);
CREATE INDEX IF NOT EXISTS zones_name_idx ON zones (name);

CREATE TABLE IF NOT EXISTS reservations (
    reservation_id UUID PRIMARY KEY,
    tourist_id UUID NOT NULL,
    tourist_name VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    number_of_people INTEGER NOT NULL,
    booked_for TIMESTAMPTZ NOT NULL,
    notes TEXT,
    cancellation_reason TEXT,
    idempotency_key VARCHAR(100),
    request_fingerprint VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT reservations_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT reservations_total_check CHECK (total >= 0),
    CONSTRAINT reservations_people_check CHECK (number_of_people > 0)
);

-- Compatibilidad con bases creadas antes de agregar idempotencia.
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS reservations_tourist_idx ON reservations (tourist_id);
CREATE INDEX IF NOT EXISTS reservations_status_idx ON reservations (status);
CREATE INDEX IF NOT EXISTS reservations_booked_for_idx ON reservations (booked_for);
CREATE INDEX IF NOT EXISTS reservations_capacity_idx ON reservations (booked_for, status);
CREATE UNIQUE INDEX IF NOT EXISTS reservations_idempotency_idx
    ON reservations (tourist_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Las reservas nuevas nacen CONFIRMED. Normaliza reservas PENDING de versiones anteriores.
UPDATE reservations
SET status = 'CONFIRMED',
    confirmed_at = COALESCE(confirmed_at, updated_at)
WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS reservation_items (
    reservation_item_id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL
        REFERENCES reservations (reservation_id) ON DELETE CASCADE,
    service_id UUID NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    provider_id UUID NOT NULL,
    provider_name VARCHAR(150) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    people INTEGER NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT reservation_items_people_check CHECK (people > 0),
    CONSTRAINT reservation_items_unit_price_check CHECK (unit_price >= 0),
    CONSTRAINT reservation_items_total_check CHECK (total >= 0),
    CONSTRAINT reservation_items_position_check CHECK (position >= 0),
    CONSTRAINT reservation_items_service_unique UNIQUE (reservation_id, service_id),
    CONSTRAINT reservation_items_position_unique UNIQUE (reservation_id, position)
);

CREATE INDEX IF NOT EXISTS reservation_items_reservation_idx ON reservation_items (reservation_id);
CREATE INDEX IF NOT EXISTS reservation_items_service_idx ON reservation_items (service_id);
CREATE INDEX IF NOT EXISTS reservation_items_provider_idx ON reservation_items (provider_id);

-- Outbox propio de PostgreSQL: la reserva y su evento se confirman en la misma transacción.
CREATE TABLE IF NOT EXISTS reservation_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    locked_by VARCHAR(100),
    locked_until TIMESTAMPTZ
);

-- Compatibilidad con bases creadas antes del claim atómico del relay.
ALTER TABLE reservation_outbox ADD COLUMN IF NOT EXISTS locked_by VARCHAR(100);
ALTER TABLE reservation_outbox ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS reservation_outbox_pending_idx
    ON reservation_outbox (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS reservation_outbox_lease_idx
    ON reservation_outbox (status, locked_until);
