-- MS01-25: estado de aprobación para el auto-registro de guías y gestoras de destinos.
-- Los registros creados antes de esta migración fueron dados de alta por un administrador, así que se consideran
-- aprobados; solo los nuevos auto-registros públicos nacen PENDIENTE.

ALTER TABLE guide_profiles ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
ALTER TABLE guide_profiles ADD CONSTRAINT ck_guide_profiles_registration_status
    CHECK (registration_status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'));
UPDATE guide_profiles SET registration_status = 'APROBADO';
CREATE INDEX ix_guide_profiles_registration_status ON guide_profiles (registration_status);

ALTER TABLE destination_manager_profiles ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
ALTER TABLE destination_manager_profiles ADD CONSTRAINT ck_destination_manager_profiles_registration_status
    CHECK (registration_status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'));
UPDATE destination_manager_profiles SET registration_status = 'APROBADO';
CREATE INDEX ix_destination_manager_profiles_registration_status
    ON destination_manager_profiles (registration_status);
