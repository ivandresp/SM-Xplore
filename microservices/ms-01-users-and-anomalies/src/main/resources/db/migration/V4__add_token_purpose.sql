-- Distingue los tokens de recuperación de contraseña de las invitaciones de cuenta.

ALTER TABLE password_reset_tokens ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'PASSWORD_RESET';

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT chk_password_reset_tokens_purpose
    CHECK (purpose IN ('PASSWORD_RESET', 'ACCOUNT_INVITATION'));
