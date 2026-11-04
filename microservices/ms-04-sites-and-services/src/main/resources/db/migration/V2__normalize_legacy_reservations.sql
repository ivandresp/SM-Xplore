-- Migración de datos heredados.
--
-- Las reservas creadas antes de este servicio nacían en PENDING y requerían una
-- confirmación manual. El flujo actual crea la reserva ya CONFIRMED, así que se
-- normaliza el histórico y, en la misma migración, se publica un evento
-- `bookings.reservation.changed.v1` por cada reserva migrada para que ms-05 y la
-- bandeja de notificaciones se pongan al día. Los eventos usan el mismo outbox
-- que el flujo normal: si Kafka está caído, el relay los publica después.

WITH promoted AS (
    UPDATE reservations
    SET status = 'CONFIRMED',
        confirmed_at = COALESCE(confirmed_at, updated_at)
    WHERE status = 'PENDING'
    RETURNING reservation_id, tourist_id, status, updated_at
),
tracked AS (
    SELECT promoted.*, gen_random_uuid() AS event_id
    FROM promoted
    WHERE EXISTS (
        SELECT 1
        FROM reservation_items item
        WHERE item.reservation_id = promoted.reservation_id
    )
),
payload AS (
    SELECT tracked.event_id,
           tracked.reservation_id,
           tracked.tourist_id,
           tracked.status,
           tracked.updated_at,
           jsonb_agg(
               jsonb_build_object(
                   'itemType', 'SERVICE',
                   'itemId', item.service_id,
                   'providerId', item.provider_id,
                   'serviceName', item.service_name,
                   'people', item.people,
                   'total', item.total
               )
               ORDER BY item.position
           ) AS items
    FROM tracked
    JOIN reservation_items item ON item.reservation_id = tracked.reservation_id
    GROUP BY tracked.event_id,
             tracked.reservation_id,
             tracked.tourist_id,
             tracked.status,
             tracked.updated_at
)
INSERT INTO reservation_outbox (
    event_id,
    event_type,
    aggregate_id,
    event_json,
    occurred_at,
    status,
    attempts,
    next_attempt_at,
    created_at
)
SELECT payload.event_id,
       'bookings.reservation.changed',
       payload.reservation_id,
       jsonb_build_object(
           'eventId', payload.event_id,
           'eventType', 'bookings.reservation.changed',
           'eventVersion', 1,
           'occurredAt', to_jsonb(payload.updated_at),
           'aggregateId', payload.reservation_id,
           'correlationId', 'migration-v2:' || payload.reservation_id,
           'producer', 'ms-04',
           'payload', jsonb_build_object(
               'reservationId', payload.reservation_id,
               'touristId', payload.tourist_id,
               'status', payload.status,
               'items', payload.items,
               'updatedAt', to_jsonb(payload.updated_at)
           )
       )::text,
       payload.updated_at,
       'PENDING',
       0,
       now(),
       now()
FROM payload;
