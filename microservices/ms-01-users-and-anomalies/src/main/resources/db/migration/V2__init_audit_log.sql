-- Auditoría de acciones administrativas críticas (Admin US10).

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users (id),
    actor_email VARCHAR(150) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_user_id UUID REFERENCES users (id),
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_user_id);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_user_id);
