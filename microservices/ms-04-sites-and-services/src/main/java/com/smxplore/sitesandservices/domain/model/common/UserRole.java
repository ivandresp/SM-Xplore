package com.smxplore.sitesandservices.domain.model.common;

import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;

import java.util.Locale;

public enum UserRole {
    ADMIN,
    GUIDE,
    DESTINATION_MANAGER,
    SERVICE_PROVIDER,
    TOURIST;

    public static UserRole fromClaim(String role) {
        return parse(normalize(role));
    }

    /** Reads roles persisted before the English-code migration (for example, old notifications). */
    public static UserRole fromStored(String role) {
        return fromClaim(role);
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) {
            throw new InvalidJwtException("JWT role is required");
        }
        String value = role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
        value = value.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "TURISTA" -> "TOURIST";
            case "GUIA" -> "GUIDE";
            case "PRESTADORA_SERVICIOS" -> "SERVICE_PROVIDER";
            case "GESTORA_DESTINOS" -> "DESTINATION_MANAGER";
            default -> value;
        };
    }

    private static UserRole parse(String role) {
        try {
            return valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtException("Unsupported JWT role: " + role);
        }
    }
}
