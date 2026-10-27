-- Autenticación multifactor TOTP para administradores y operadores (RNF01).

CREATE TABLE mfa_enrollments (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_step BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_mfa_recovery_codes_hash ON mfa_recovery_codes (code_hash);
CREATE INDEX ix_mfa_recovery_codes_user ON mfa_recovery_codes (user_id);
