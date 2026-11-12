-- MS01-B8b: registro publico de gestoras de destino con aprobacion de un administrador.
-- La gestora nace PENDIENTE y no puede iniciar sesion hasta que un administrador la apruebe.
ALTER TABLE destination_manager_profiles ADD COLUMN registration_status VARCHAR(30);

-- Las gestoras que ya existian fueron creadas por un administrador (alta interna o cambio de rol), es decir, ya
-- estaban aprobadas por la autoridad que revisa. Marcarlas como PENDIENTE las dejaria fuera de la plataforma.
UPDATE destination_manager_profiles SET registration_status = 'APROBADO' WHERE registration_status IS NULL;

ALTER TABLE destination_manager_profiles ALTER COLUMN registration_status SET NOT NULL;

ALTER TABLE destination_manager_profiles
    ADD CONSTRAINT chk_destination_manager_registration_status CHECK (
        registration_status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')
    );

CREATE INDEX ix_destination_manager_profiles_status ON destination_manager_profiles (registration_status);
