-- V2: symbolic reservations. One reservation is a single item and is born CONFIRMED.
-- This migration backfills legacy reservation_items and normalizes old statuses.
-- WARNING: reservations without an item cannot be represented and are deleted.

ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_type       VARCHAR(20);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_id         UUID;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_starts_at  TIMESTAMP WITH TIME ZONE;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS unit_price      NUMERIC(14, 2);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

-- Copy one legacy item per reservation when the old join table still exists.
DO $$
BEGIN
    IF to_regclass('public.reservation_items') IS NOT NULL THEN
        EXECUTE $migration$
            UPDATE reservations r
            SET item_type = i.type,
                item_id = i.item_id,
                unit_price = i.price
            FROM (
                SELECT DISTINCT ON (reservation_id)
                       reservation_id, type, item_id, price
                FROM reservation_items
                ORDER BY reservation_id, id
            ) i
            WHERE i.reservation_id = r.id
              AND r.item_type IS NULL
        $migration$;
    END IF;
END $$;

UPDATE reservations
SET unit_price = CASE WHEN number_of_people > 0 THEN total / number_of_people ELSE 0 END
WHERE unit_price IS NULL;

UPDATE reservations r SET item_starts_at = a.starts_at
FROM actividad a
WHERE r.item_type = 'ACTIVITY' AND r.item_id = a.id AND r.item_starts_at IS NULL;

UPDATE reservations r SET item_starts_at = e.starts_at
FROM evento e
WHERE r.item_type = 'EVENT' AND r.item_id = e.id AND r.item_starts_at IS NULL;

UPDATE reservations SET item_starts_at = booked_for WHERE item_starts_at IS NULL;

DELETE FROM reservations
WHERE item_type IS NULL OR item_id IS NULL OR item_starts_at IS NULL;

UPDATE reservations SET total = unit_price * number_of_people
WHERE total <> unit_price * number_of_people;
UPDATE reservations SET status = 'CONFIRMED' WHERE status = 'PENDING';
UPDATE reservations SET status = 'CANCELLED' WHERE status IN ('REJECTED', 'CANCELLATION_REQUESTED');

ALTER TABLE reservations ALTER COLUMN item_type      SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN item_id        SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN item_starts_at SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN unit_price     SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN status SET DEFAULT 'CONFIRMED';

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_status_valid;
ALTER TABLE reservations ADD CONSTRAINT reservations_status_valid
    CHECK (status IN ('CONFIRMED', 'CANCELLED', 'NO_SHOW', 'COMPLETED'));

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_unit_price_non_negative;
ALTER TABLE reservations ADD CONSTRAINT reservations_unit_price_non_negative CHECK (unit_price >= 0);

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_item_type_valid;
ALTER TABLE reservations ADD CONSTRAINT reservations_item_type_valid CHECK (item_type IN ('EVENT', 'ACTIVITY'));

ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_total_matches_price;
ALTER TABLE reservations ADD CONSTRAINT reservations_total_matches_price
    CHECK (total = unit_price * number_of_people);

DROP TABLE IF EXISTS reservation_items;

CREATE INDEX IF NOT EXISTS reservations_tourist_idx ON reservations (tourist_id);
CREATE INDEX IF NOT EXISTS reservations_status_idx ON reservations (status);
CREATE INDEX IF NOT EXISTS reservations_guide_idx ON reservations (guide_id);
CREATE INDEX IF NOT EXISTS reservations_item_idx ON reservations (item_type, item_id, status);
CREATE INDEX IF NOT EXISTS reservations_tourist_status_idx ON reservations (tourist_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS reservations_tourist_idempotency_idx
    ON reservations (tourist_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS reservation_code_seq;
