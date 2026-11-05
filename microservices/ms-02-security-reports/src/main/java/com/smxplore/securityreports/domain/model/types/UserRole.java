package com.smxplore.securityreports.domain.model.types;

import java.util.Locale;

/**
 * Roles of the JWT contract. The English codes are the only wire values; a token issued before
 * ms-01 migrated them ({@code V10__english_role_codes}) still carries the Spanish alias, so the
 * aliases are read-only compatibility during the migration window.
 */
public enum UserRole {
    ADMIN,
    GUIDE,
    SERVICE_PROVIDER,
    DESTINATION_MANAGER,
    TOURIST;

    /** Null when the claim is missing or unknown, so the caller decides (usually no authority). */
    public static UserRole fromClaimOrNull(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String value = role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
        value = value.trim().toUpperCase(Locale.ROOT);
        value = switch (value) {
            case "TURISTA" -> "TOURIST";
            case "GUIA" -> "GUIDE";
            case "PRESTADORA_SERVICIOS", "PRESTADORA" -> "SERVICE_PROVIDER";
            case "GESTORA_DESTINOS" -> "DESTINATION_MANAGER";
            case "ADMINISTRADOR" -> "ADMIN";
            default -> value;
        };
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
