-- Anomalías operativas y de seguridad (Admin US11-US14, RF99-RF102).
-- La detección es automática; el administrador clasifica por severidad y resuelve.

CREATE TABLE anomalies (
    id UUID PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users (id) ON DELETE SET NULL,
    resolution_note TEXT,
    CONSTRAINT chk_anomalies_type CHECK (
        type IN ('ACCESO_SOSPECHOSO', 'CAIDA_SERVICIO', 'PICO_SOLICITUDES')
    ),
    CONSTRAINT chk_anomalies_severity CHECK (
        severity IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')
    ),
    CONSTRAINT chk_anomalies_status CHECK (
        status IN ('DETECTADA', 'RESUELTA')
    ),
    CONSTRAINT chk_anomalies_resolution CHECK (
        (status = 'DETECTADA' AND resolved_at IS NULL AND resolved_by IS NULL AND resolution_note IS NULL)
        OR (status = 'RESUELTA' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
    )
);

CREATE INDEX ix_anomalies_status_severity ON anomalies (status, severity, detected_at DESC);
CREATE INDEX ix_anomalies_type_detected_at ON anomalies (type, detected_at DESC);
CREATE INDEX ix_anomalies_detected_at ON anomalies (detected_at DESC);

CREATE TABLE anomaly_thresholds (
    type VARCHAR(40) PRIMARY KEY,
    threshold_value INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    severity VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_anomaly_thresholds_type CHECK (
        type IN ('ACCESO_SOSPECHOSO', 'CAIDA_SERVICIO', 'PICO_SOLICITUDES')
    ),
    CONSTRAINT chk_anomaly_thresholds_severity CHECK (
        severity IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')
    ),
    CONSTRAINT chk_anomaly_thresholds_value CHECK (
        threshold_value BETWEEN 1 AND 1000000
    ),
    CONSTRAINT chk_anomaly_thresholds_window CHECK (
        window_seconds BETWEEN 10 AND 3600
    )
);

INSERT INTO anomaly_thresholds (type, threshold_value, window_seconds, severity, enabled, updated_at, updated_by)
VALUES
    ('ACCESO_SOSPECHOSO', 5, 300, 'ALTA', TRUE, now(), NULL),
    ('CAIDA_SERVICIO', 2, 60, 'CRITICA', TRUE, now(), NULL),
    ('PICO_SOLICITUDES', 300, 60, 'MEDIA', TRUE, now(), NULL);
