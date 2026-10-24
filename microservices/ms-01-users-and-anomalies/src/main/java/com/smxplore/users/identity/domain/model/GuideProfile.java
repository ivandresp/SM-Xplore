package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.time.LocalDate;

public record GuideProfile(Dni dni, String license, LocalDate startOperationDate) implements Profile {

    public GuideProfile {
        if (dni == null) {
            throw new ValidationException("El documento del guía es obligatorio");
        }
        if (license == null || license.isBlank()) {
            throw new ValidationException("La licencia del guía es obligatoria");
        }
        license = license.trim();
        if (license.length() > 50) {
            throw new ValidationException("La licencia del guía no puede superar 50 caracteres");
        }
    }

    @Override
    public Role role() {
        return Role.GUIA;
    }
}
