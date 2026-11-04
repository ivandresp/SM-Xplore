-- V2: symbolic reservations. One reservation is a single item and is born CONFIRMED.
--
-- Safety contract:
--   * The original rows are archived before anything is changed.
--   * Reservations that cannot be represented (no item, multiple items, or an item
--     missing from the catalog) are moved to reservations_quarantine, never deleted.
--   * reservation_items is copied to reservation_items_legacy before the original
--     table is dropped, so multi-item history is preserved.
--   * The original total, status and booked_for stay in reservations_legacy.
--
-- An operator must review reservations_quarantine after the upgrade.

ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_type       VARCHAR(20);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_id         UUID;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_starts_at  TIMESTAMP WITH TIME ZONE;
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS unit_price      NUMERIC(14, 2);
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

-- Full snapshot of the pre-migration rows, including the original total/status.
DO $$
BEGIN
    IF to_regclass('reservations_legacy') IS NULL THEN
        EXECUTE 'CREATE TABLE reservations_legacy AS SELECT *, now() AS archived_at FROM reservations';
    END IF;
END $$;

-- Standalone copy of every legacy line item, before the original table is dropped.
DO $$
BEGIN
    IF to_regclass('reservation_items') IS NOT NULL
       AND to_regclass('reservation_items_legacy') IS NULL THEN
        EXECUTE 'CREATE TABLE reservation_items_legacy AS SELECT *, now() AS archived_at FROM reservation_items';
    END IF;
END $$;

-- Rows that cannot become a symbolic reservation are preserved here.
CREATE TABLE IF NOT EXISTS reservations_quarantine (
    LIKE reservations INCLUDING DEFAULTS,
    reason          text        NOT NULL,
    quarantined_at  timestamptz NOT NULL DEFAULT now()
);

-- One legacy item per reservation is copied only when the reservation has exactly one.
DO $$
BEGIN
    IF to_regclass('reservation_items') IS NOT NULL THEN
        EXECUTE $sql$
            UPDATE reservations r
            SET item_type = i.type,
                item_id = i.item_id,
                unit_price = i.price
            FROM reservation_items i
            WHERE i.reservation_id = r.id
              AND r.item_type IS NULL
              AND (SELECT count(*) FROM reservation_items x WHERE x.reservation_id = r.id) = 1
        $sql$;
    END IF;
END $$;

-- Quarantine every row that cannot be represented. No row is deleted here.
DO $$
BEGIN
    IF to_regclass('reservation_items') IS NOT NULL THEN
        EXECUTE $sql$
            INSERT INTO reservations_quarantine
            SELECT r.*,
                   CASE
                       WHEN r.item_type IS NULL
                            AND (SELECT count(*) FROM reservation_items i WHERE i.reservation_id = r.id) = 0
                           THEN 'no item'
                       WHEN r.item_type IS NULL
                            AND (SELECT count(*) FROM reservation_items i WHERE i.reservation_id = r.id) > 1
                           THEN 'multiple items'
                       WHEN r.item_type = 'ACTIVITY'
                            AND NOT EXISTS (SELECT 1 FROM actividad a WHERE a.id = r.item_id)
                           THEN 'activity not found'
                       WHEN r.item_type = 'EVENT'
                            AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.id = r.item_id)
                           THEN 'event not found'
                       ELSE 'unresolvable'
                   END,
                   now()
            FROM reservations r
            WHERE (
                r.item_type IS NULL
                OR r.item_id IS NULL
                OR (r.item_type IS NULL
                    AND (SELECT count(*) FROM reservation_items i WHERE i.reservation_id = r.id) <> 1)
                OR (r.item_type = 'ACTIVITY'
                    AND NOT EXISTS (SELECT 1 FROM actividad a WHERE a.id = r.item_id))
                OR (r.item_type = 'EVENT'
                    AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.id = r.item_id))
            )
            AND NOT EXISTS (SELECT 1 FROM reservations_quarantine q WHERE q.id = r.id)
        $sql$;
    ELSE
        EXECUTE $sql$
            INSERT INTO reservations_quarantine
            SELECT r.*,
                   CASE
                       WHEN r.item_type IS NULL OR r.item_id IS NULL
                           THEN 'no item'
                       WHEN r.item_type = 'ACTIVITY'
                            AND NOT EXISTS (SELECT 1 FROM actividad a WHERE a.id = r.item_id)
                           THEN 'activity not found'
                       WHEN r.item_type = 'EVENT'
                            AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.id = r.item_id)
                           THEN 'event not found'
                       ELSE 'unresolvable'
                   END,
                   now()
            FROM reservations r
            WHERE (
                r.item_type IS NULL
                OR r.item_id IS NULL
                OR (r.item_type = 'ACTIVITY'
                    AND NOT EXISTS (SELECT 1 FROM actividad a WHERE a.id = r.item_id))
                OR (r.item_type = 'EVENT'
                    AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.id = r.item_id))
            )
            AND NOT EXISTS (SELECT 1 FROM reservations_quarantine q WHERE q.id = r.id)
        $sql$;
    END IF;
END $$;

DELETE FROM reservations r
USING reservations_quarantine q
WHERE r.id = q.id;

UPDATE reservations
SET unit_price = CASE WHEN number_of_people > 0 THEN total / number_of_people ELSE 0 END
WHERE unit_price IS NULL;

-- Prefer the catalog start over the old (client-supplied) booked_for.
UPDATE reservations r SET item_starts_at = a.starts_at
FROM actividad a
WHERE r.item_type = 'ACTIVITY' AND r.item_id = a.id AND r.item_starts_at IS NULL;

UPDATE reservations r SET item_starts_at = e.starts_at
FROM evento e
WHERE r.item_type = 'EVENT' AND r.item_id = e.id AND r.item_starts_at IS NULL;

UPDATE reservations SET item_starts_at = booked_for WHERE item_starts_at IS NULL;

-- One column, one meaning: the symbolic model uses the catalog start.
UPDATE reservations SET booked_for = item_starts_at
WHERE booked_for IS DISTINCT FROM item_starts_at;

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

-- Duplicate idempotency keys can only come from restored dumps or pre-lock rows.
WITH duplicates AS (
    SELECT id,
           row_number() OVER (PARTITION BY tourist_id, idempotency_key ORDER BY booked_at DESC, id) AS rn
    FROM reservations
    WHERE idempotency_key IS NOT NULL
)
UPDATE reservations
SET idempotency_key = NULL
FROM duplicates d
WHERE reservations.id = d.id AND d.rn > 1;

DROP TABLE IF EXISTS reservation_items;

DROP INDEX IF EXISTS reservations_tourist_idx;

CREATE INDEX IF NOT EXISTS reservations_status_idx ON reservations (status);
CREATE INDEX IF NOT EXISTS reservations_guide_idx ON reservations (guide_id);
CREATE INDEX IF NOT EXISTS reservations_provider_idx ON reservations (provider_id);
CREATE INDEX IF NOT EXISTS reservations_item_idx ON reservations (item_type, item_id, status);
CREATE INDEX IF NOT EXISTS reservations_tourist_status_idx ON reservations (tourist_id, status);
CREATE INDEX IF NOT EXISTS reservations_status_starts_idx ON reservations (status, item_starts_at);
CREATE UNIQUE INDEX IF NOT EXISTS reservations_tourist_idempotency_idx
    ON reservations (tourist_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS reservation_code_seq;

DO $$
DECLARE
    quarantined integer;
BEGIN
    SELECT count(*) INTO quarantined FROM reservations_quarantine;
    IF quarantined > 0 THEN
        RAISE NOTICE '% reservation(s) moved to reservations_quarantine; review them before releasing the feature', quarantined;
    END IF;
END $$;
