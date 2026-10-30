-- Soporte para el listado y la búsqueda administrativa de usuarios.

CREATE INDEX ix_users_full_name_lower ON users (LOWER(full_name));
CREATE INDEX ix_users_signed_up_at ON users (signed_up_at DESC);
