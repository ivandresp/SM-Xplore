CREATE TABLE IF NOT EXISTS evento (
    id                  uuid PRIMARY KEY,
    sitio_id            uuid NOT NULL,
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

CREATE INDEX IF NOT EXISTS evento_sitio_idx ON evento (sitio_id);
CREATE INDEX IF NOT EXISTS evento_name_idx ON evento (name);
CREATE INDEX IF NOT EXISTS actividad_sitio_idx ON actividad (sitio_id);
CREATE INDEX IF NOT EXISTS actividad_category_idx ON actividad (category);

CREATE TABLE IF NOT EXISTS reservations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tourist_id       UUID                     NOT NULL,
    number_of_people INTEGER                  NOT NULL,
    total            NUMERIC(18, 2)           NOT NULL,
    payment_method   VARCHAR(20)              NOT NULL,
    status           VARCHAR(30)              NOT NULL,
    booked_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    booked_for       TIMESTAMP WITH TIME ZONE NOT NULL,
    notes            TEXT,
    provider_id      UUID,
    guide_id         UUID,
    code             VARCHAR(20),
    CONSTRAINT reservations_number_of_people_positive CHECK (number_of_people > 0),
    CONSTRAINT reservations_total_non_negative CHECK (total >= 0),
    CONSTRAINT reservations_booked_for_after_booked_at CHECK (booked_for > booked_at),
    CONSTRAINT reservations_payment_method_valid CHECK (payment_method IN ('CARD', 'CASH', 'TRANSFER')),
    CONSTRAINT reservations_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLATION_REQUESTED', 'REJECTED', 'CANCELLED', 'NO_SHOW', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS reservations_tourist_idx ON reservations (tourist_id);
CREATE INDEX IF NOT EXISTS reservations_status_idx ON reservations (status);
CREATE INDEX IF NOT EXISTS reservations_guide_idx ON reservations (guide_id);

CREATE SEQUENCE IF NOT EXISTS reservation_code_seq;

CREATE TABLE IF NOT EXISTS reservation_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID           NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    item_id        UUID           NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    price          NUMERIC(14, 2) NOT NULL,
    CONSTRAINT reservation_items_price_non_negative CHECK (price >= 0),
    CONSTRAINT reservation_items_type_valid CHECK (type IN ('EVENT', 'ACTIVITY'))
);

CREATE INDEX IF NOT EXISTS reservation_items_reservation_idx ON reservation_items (reservation_id);
CREATE INDEX IF NOT EXISTS reservation_items_item_idx ON reservation_items (item_id);
CREATE INDEX IF NOT EXISTS reservation_items_type_idx ON reservation_items (type);
