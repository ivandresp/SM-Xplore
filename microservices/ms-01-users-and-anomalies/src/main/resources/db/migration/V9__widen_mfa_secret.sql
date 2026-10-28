-- Amplía la columna para almacenar el secreto TOTP cifrado con AES-GCM en Base64 (prefijo v1:).
ALTER TABLE mfa_enrollments ALTER COLUMN secret TYPE VARCHAR(255);
