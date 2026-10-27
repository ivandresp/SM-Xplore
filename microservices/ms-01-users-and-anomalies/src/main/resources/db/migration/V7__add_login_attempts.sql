-- Protección contra fuerza bruta: intentos fallidos y bloqueo temporal por cuenta (RNF01/RNF02).

CREATE TABLE login_attempts (
    email VARCHAR(150) PRIMARY KEY,
    failed_count INTEGER NOT NULL,
    first_failed_at TIMESTAMPTZ NOT NULL,
    last_failed_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ
);

CREATE INDEX ix_login_attempts_locked_until ON login_attempts (locked_until);
