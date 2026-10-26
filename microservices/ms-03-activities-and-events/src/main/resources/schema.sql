CREATE TABLE IF NOT EXISTS evento (
    id                  uuid PRIMARY KEY,
    sitio_id            uuid NOT NULL,
    sitio_nombre        varchar(150),
    manager_id          uuid,
    name                varchar(150) NOT NULL,
    category            varchar(50) NOT NULL,
    status              varchar(30) NOT NULL DEFAULT 'DRAFT',
    price               numeric(12, 2) NOT NULL DEFAULT 0,
    max_capacity        integer NOT NULL DEFAULT 1,
    starts_at           timestamptz NOT NULL,
    ends_at             timestamptz NOT NULL,
    description         text,
    expected_attendance integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS actividad (
    id            uuid PRIMARY KEY,
    sitio_id      uuid NOT NULL,
    guia_id       uuid,
    name          varchar(150) NOT NULL,
    category      varchar(50) NOT NULL,
    status        varchar(30) NOT NULL DEFAULT 'DRAFT',
    price         numeric(12, 2) NOT NULL DEFAULT 0,
    max_capacity  integer NOT NULL DEFAULT 1,
    duration_mins integer NOT NULL DEFAULT 1,
    starts_at     timestamptz NOT NULL,
    ends_at       timestamptz NOT NULL,
    description   text
);

ALTER TABLE evento ADD COLUMN IF NOT EXISTS sitio_nombre varchar(150);
ALTER TABLE evento ADD COLUMN IF NOT EXISTS manager_id uuid;

CREATE INDEX IF NOT EXISTS evento_sitio_idx ON evento (sitio_id);
CREATE INDEX IF NOT EXISTS evento_manager_idx ON evento (manager_id);
CREATE INDEX IF NOT EXISTS evento_name_idx ON evento (name);
CREATE INDEX IF NOT EXISTS actividad_sitio_idx ON actividad (sitio_id);
CREATE INDEX IF NOT EXISTS actividad_category_idx ON actividad (category);

CREATE TABLE IF NOT EXISTS reservations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tourist_id       UUID                     NOT NULL,
    item_type        VARCHAR(20)              NOT NULL,
    item_id          UUID                     NOT NULL,
    unit_price       NUMERIC(14, 2)           NOT NULL,
    number_of_people INTEGER                  NOT NULL,
    total            NUMERIC(18, 2)           NOT NULL,
    payment_method   VARCHAR(20)              NOT NULL,
    status           VARCHAR(30)              NOT NULL DEFAULT 'CONFIRMED',
    booked_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    booked_for       TIMESTAMP WITH TIME ZONE NOT NULL,
    notes            TEXT,
    provider_id      UUID,
    guide_id         UUID,
    code             VARCHAR(20),
    CONSTRAINT reservations_number_of_people_positive CHECK (number_of_people > 0),
    CONSTRAINT reservations_total_non_negative CHECK (total >= 0),
    CONSTRAINT reservations_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT reservations_booked_for_after_booked_at CHECK (booked_for > booked_at),
    CONSTRAINT reservations_payment_method_valid CHECK (payment_method IN ('CARD', 'CASH', 'TRANSFER')),
    CONSTRAINT reservations_status_valid CHECK (status IN ('CONFIRMED', 'CANCELLED', 'NO_SHOW', 'COMPLETED')),
    CONSTRAINT reservations_item_type_valid CHECK (item_type IN ('EVENT', 'ACTIVITY'))
);

-- Existing installations: add the single-item columns, then normalise the old rows.
-- This is a no-op once the migration has run (the columns are already NOT NULL).
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_type  VARCHAR(20);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_id    UUID;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS unit_price NUMERIC(14, 2);

DELETE FROM reservations WHERE item_type IS NULL OR item_id IS NULL;
UPDATE reservations SET unit_price = 0 WHERE unit_price IS NULL;
UPDATE reservations SET status = 'CONFIRMED' WHERE status = 'PENDING';
UPDATE reservations SET status = 'CANCELLED' WHERE status IN ('REJECTED', 'CANCELLATION_REQUESTED');

ALTER TABLE reservations ALTER COLUMN item_type  SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN item_id    SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN unit_price SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN status SET DEFAULT 'CONFIRMED';

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_status_valid;
ALTER TABLE reservations ADD CONSTRAINT reservations_status_valid
    CHECK (status IN ('CONFIRMED', 'CANCELLED', 'NO_SHOW', 'COMPLETED'));

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_unit_price_non_negative;
ALTER TABLE reservations ADD CONSTRAINT reservations_unit_price_non_negative CHECK (unit_price >= 0);

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_item_type_valid;
ALTER TABLE reservations ADD CONSTRAINT reservations_item_type_valid CHECK (item_type IN ('EVENT', 'ACTIVITY'));

-- Symbolic reservations have a single item: the join table is gone.
DROP TABLE IF EXISTS reservation_items;

CREATE INDEX IF NOT EXISTS reservations_tourist_idx ON reservations (tourist_id);
CREATE INDEX IF NOT EXISTS reservations_status_idx ON reservations (status);
CREATE INDEX IF NOT EXISTS reservations_guide_idx ON reservations (guide_id);
CREATE INDEX IF NOT EXISTS reservations_item_idx ON reservations (item_type, item_id, status);

CREATE SEQUENCE IF NOT EXISTS reservation_code_seq;
