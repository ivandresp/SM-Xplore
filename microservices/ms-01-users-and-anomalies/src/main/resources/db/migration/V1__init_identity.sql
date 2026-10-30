CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    auth_version BIGINT NOT NULL DEFAULT 0,
    signed_up_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_role CHECK (
        role IN ('TURISTA', 'GUIA', 'PRESTADORA_SERVICIOS', 'GESTORA_DESTINOS', 'ADMIN')
    ),
    CONSTRAINT chk_users_status CHECK (
        status IN ('ACTIVO', 'BLOQUEADO', 'SUSPENDIDO', 'ELIMINADO')
    )
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_users_role_status ON users (role, status);

CREATE TABLE tourist_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    dni_type VARCHAR(20) NOT NULL,
    dni_number VARCHAR(30) NOT NULL,
    preferred_language VARCHAR(50),
    birth_date DATE,
    nationality VARCHAR(80)
);

CREATE UNIQUE INDEX ux_tourist_profiles_dni ON tourist_profiles (dni_number);

CREATE TABLE guide_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    dni_type VARCHAR(20) NOT NULL,
    dni_number VARCHAR(30) NOT NULL,
    license VARCHAR(50) NOT NULL,
    start_operation_date DATE
);

CREATE UNIQUE INDEX ux_guide_profiles_dni ON guide_profiles (dni_number);
CREATE UNIQUE INDEX ux_guide_profiles_license ON guide_profiles (license);

CREATE TABLE provider_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    nit VARCHAR(30) NOT NULL,
    business_type VARCHAR(50),
    registration_status VARCHAR(30) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    start_operation_date DATE,
    CONSTRAINT chk_provider_registration_status CHECK (
        registration_status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')
    ),
    CONSTRAINT chk_provider_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_provider_profiles_nit ON provider_profiles (nit);
CREATE INDEX ix_provider_profiles_status ON provider_profiles (registration_status);

CREATE TABLE destination_manager_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    nit VARCHAR(30) NOT NULL,
    start_operation_date DATE
);

CREATE UNIQUE INDEX ux_destination_manager_profiles_nit
    ON destination_manager_profiles (nit);

CREATE TABLE admin_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    dni_type VARCHAR(20),
    dni_number VARCHAR(30),
    last_access_date TIMESTAMPTZ
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
