-- MS01-18: verificación de correo electrónico.
-- Los usuarios creados antes de esta migración se consideran verificados: sus cuentas ya existían y no se les puede
-- exigir retroactivamente una verificación que el producto nunca envió.
CREATE TABLE email_verifications (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    verified_at TIMESTAMPTZ NOT NULL
);

INSERT INTO email_verifications (user_id, verified_at)
SELECT id, NOW() FROM users;

-- El mismo almacén de tokens (con hash) sirve para la verificación de correo; se añade el propósito.
ALTER TABLE password_reset_tokens DROP CONSTRAINT chk_password_reset_tokens_purpose;

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT chk_password_reset_tokens_purpose
    CHECK (purpose IN ('PASSWORD_RESET', 'ACCOUNT_INVITATION', 'EMAIL_VERIFICATION'));

CREATE INDEX ix_email_verifications_verified_at ON email_verifications (verified_at);
