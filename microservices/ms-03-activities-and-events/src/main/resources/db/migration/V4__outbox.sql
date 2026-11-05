-- V4: transactional outbox for domain events.
CREATE TABLE IF NOT EXISTS outbox_events (
    id              uuid PRIMARY KEY,
    event_type      varchar(100) NOT NULL,
    event_version   integer      NOT NULL,
    aggregate_id    uuid         NOT NULL,
    correlation_id  uuid,
    payload         jsonb        NOT NULL,
    occurred_at     timestamptz  NOT NULL,
    published_at    timestamptz,
    attempts        integer      NOT NULL DEFAULT 0,
    last_error      text,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS outbox_events_pending_idx
    ON outbox_events (occurred_at)
    WHERE published_at IS NULL;
