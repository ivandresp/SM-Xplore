package com.smxplore.sitesandservices.domain.model.common;

import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;

import java.util.Locale;

public enum UserRole {
    ADMIN,
    GESTORA_DESTINOS,
    PRESTADORA_SERVICIOS,
    TURISTA;

    public static UserRole fromClaim(String role) {
        return parse(normalize(role));
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) {
            throw new InvalidJwtException("JWT role is required");
        }
        String value = role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static UserRole parse(String role) {
        try {
            return valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException("Unsupported JWT role: " + role);
        }
    }
}
