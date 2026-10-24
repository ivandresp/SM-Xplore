package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.time.LocalDate;

public record TouristProfile(
        String dniType, String dniNumber, String preferredLanguage, LocalDate birthDate, String nationality)
        implements Profile {

    public TouristProfile {
        preferredLanguage = normalize(preferredLanguage, "es");
        nationality = normalize(nationality, null);
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de nacimiento no puede ser futura");
        }
    }

    @Override
    public Role role() {
        return Role.TURISTA;
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
