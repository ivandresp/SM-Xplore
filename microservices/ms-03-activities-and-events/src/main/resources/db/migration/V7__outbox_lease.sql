-- V7: leased outbox relay with backoff, attempt limit and retention.
--
-- Antes de esta migracion el relay leia los pendientes y los marcaba sin lock: dos replicas
-- publicaban el mismo evento y una caida a mitad del envio dejaba la fila tocada para siempre.
-- El claim usa FOR UPDATE SKIP LOCKED sobre estas columnas, y el estado explicito permite que la
-- retencion borre lo entregado sin confundirlo con lo que fallo.

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS status          varchar(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS locked_by       varchar(64),
    ADD COLUMN IF NOT EXISTS locked_until    timestamptz,
    -- DEFAULT now(): ADD COLUMN con DEFAULT constante/volatile es metadata-only, no reescribe filas.
    ADD COLUMN IF NOT EXISTS next_attempt_at timestamptz NOT NULL DEFAULT now();

-- Filas anteriores a V7 ya entregadas.
UPDATE outbox_events
SET status = 'PUBLISHED'
WHERE published_at IS NOT NULL
  AND status = 'PENDING';

-- El indice parcial anterior solo cubria published_at.
DROP INDEX IF EXISTS outbox_events_pending_idx;

-- El claim filtra por next_attempt_at y ordena por occurred_at (orden de insercion): con este
-- indice recorre las filas ya ordenadas y se detiene al llenar el lote.
CREATE INDEX IF NOT EXISTS outbox_events_claim_idx
    ON outbox_events (occurred_at, id)
    WHERE status = 'PENDING';

-- La recuperacion de leases vencidos filtra locked_until: sin indice hacia un scan por ciclo.
CREATE INDEX IF NOT EXISTS outbox_events_lease_idx
    ON outbox_events (locked_until)
    WHERE status = 'PROCESSING';

CREATE INDEX IF NOT EXISTS outbox_events_retention_idx
    ON outbox_events (created_at)
    WHERE status IN ('PUBLISHED', 'FAILED');
