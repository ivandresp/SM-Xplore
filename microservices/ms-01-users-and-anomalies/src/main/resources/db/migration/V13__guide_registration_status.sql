-- MS01-B8: registro público de guías con aprobación de una gestora.
-- El alta de un guía pasa a tener el mismo estado de revisión que ya tenía la prestadora de servicios
-- (PENDIENTE / APROBADO / RECHAZADO), pero en su propia tabla de perfil.
ALTER TABLE guide_profiles ADD COLUMN registration_status VARCHAR(30);

-- Los guías que ya existían fueron creados por un administrador (alta interna o cambio de rol), es decir, ya estaban
-- aprobados por la autoridad que revisa. Marcarlos como PENDIENTE los dejaría fuera de la plataforma.
UPDATE guide_profiles SET registration_status = 'APROBADO' WHERE registration_status IS NULL;

ALTER TABLE guide_profiles ALTER COLUMN registration_status SET NOT NULL;
ALTER TABLE guide_profiles ALTER COLUMN registration_status SET DEFAULT 'PENDIENTE';

ALTER TABLE guide_profiles
    ADD CONSTRAINT chk_guide_registration_status CHECK (
        registration_status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')
    );

CREATE INDEX ix_guide_profiles_status ON guide_profiles (registration_status);
