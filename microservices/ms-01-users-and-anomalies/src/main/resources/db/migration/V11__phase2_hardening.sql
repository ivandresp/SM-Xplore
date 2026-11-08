-- Fase 2 de seguridad (MS01-06, MS01-07, MS01-08, MS01-11).

-- MS01-06 / MS01-08: los desafíos MFA pasan a ser de un solo uso y se ligan a la versión de sesión.
CREATE TABLE mfa_challenges (
    jti UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_mfa_challenges_expires ON mfa_challenges (expires_at);

-- MS01-07: los códigos de recuperación ahora son más largos y su hash lleva sal.
ALTER TABLE mfa_recovery_codes ALTER COLUMN code_hash TYPE VARCHAR(200);

-- MS01-11: auditoría de los accesos al endpoint interno de contactos.
CREATE TABLE internal_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service VARCHAR(32) NOT NULL,
    target_user_id UUID,
    outcome VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_internal_access_log_outcome
        CHECK (outcome IN ('OK', 'NOT_FOUND', 'INVALID_SERVICE', 'INVALID_SIGNATURE', 'RATE_LIMITED'))
);

CREATE INDEX ix_internal_access_log_occurred_at ON internal_access_log (occurred_at DESC);

CREATE INDEX ix_internal_access_log_target ON internal_access_log (target_user_id);
