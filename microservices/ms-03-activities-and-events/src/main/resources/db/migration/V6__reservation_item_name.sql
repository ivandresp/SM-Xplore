-- V6: copy the activity/event name into the reservation so booking events can notify
-- tourists, guides and destination managers without a cross-service lookup.
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS item_name VARCHAR(150);
