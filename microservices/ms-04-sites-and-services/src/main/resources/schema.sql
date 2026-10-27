CREATE TABLE IF NOT EXISTS zones (
    zone_id UUID PRIMARY KEY,
    manager_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(30) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS zones_manager_idx ON zones (manager_id);
CREATE INDEX IF NOT EXISTS zones_status_idx ON zones (status);
CREATE INDEX IF NOT EXISTS zones_name_idx ON zones (name);
