-- V3: query indexes and integrity checks for the catalog and reservations.
--
-- Checks are added as NOT VALID so an existing deployment with legacy outliers
-- can still upgrade; validate them after reviewing the data.

CREATE INDEX IF NOT EXISTS evento_status_starts_idx ON evento (status, starts_at);
CREATE INDEX IF NOT EXISTS actividad_status_starts_idx ON actividad (status, starts_at);
CREATE INDEX IF NOT EXISTS reservations_tourist_status_starts_idx
    ON reservations (tourist_id, status, item_starts_at);

-- This index is a strict prefix of reservations_status_starts_idx.
DROP INDEX IF EXISTS reservations_status_idx;

ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_price_non_negative;
ALTER TABLE evento ADD CONSTRAINT evento_price_non_negative CHECK (price >= 0) NOT VALID;
ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_capacity_positive;
ALTER TABLE evento ADD CONSTRAINT evento_capacity_positive CHECK (max_capacity > 0) NOT VALID;
ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_schedule_valid;
ALTER TABLE evento ADD CONSTRAINT evento_schedule_valid CHECK (ends_at > starts_at) NOT VALID;
ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_expected_attendance_non_negative;
ALTER TABLE evento ADD CONSTRAINT evento_expected_attendance_non_negative CHECK (expected_attendance >= 0) NOT VALID;
ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_status_valid;
ALTER TABLE evento ADD CONSTRAINT evento_status_valid
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'POSTPONED', 'CANCELLED')) NOT VALID;

ALTER TABLE actividad DROP CONSTRAINT IF EXISTS actividad_price_non_negative;
ALTER TABLE actividad ADD CONSTRAINT actividad_price_non_negative CHECK (price >= 0) NOT VALID;
ALTER TABLE actividad DROP CONSTRAINT IF EXISTS actividad_capacity_positive;
ALTER TABLE actividad ADD CONSTRAINT actividad_capacity_positive CHECK (max_capacity > 0) NOT VALID;
ALTER TABLE actividad DROP CONSTRAINT IF EXISTS actividad_duration_positive;
ALTER TABLE actividad ADD CONSTRAINT actividad_duration_positive CHECK (duration_mins > 0) NOT VALID;
ALTER TABLE actividad DROP CONSTRAINT IF EXISTS actividad_schedule_valid;
ALTER TABLE actividad ADD CONSTRAINT actividad_schedule_valid CHECK (ends_at > starts_at) NOT VALID;
ALTER TABLE actividad DROP CONSTRAINT IF EXISTS actividad_status_valid;
ALTER TABLE actividad ADD CONSTRAINT actividad_status_valid
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'CANCELLED')) NOT VALID;
