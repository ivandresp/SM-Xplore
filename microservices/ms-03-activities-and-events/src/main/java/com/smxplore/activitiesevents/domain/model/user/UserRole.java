package com.smxplore.activitiesevents.domain.model.user;

import java.util.Locale;

/**
 * Platform roles. Java identifiers stay in English; the wire value used by the JWT
 * (and therefore by every service) is the Spanish code.
 */
public enum UserRole {

    TOURIST("TURISTA"),
    GUIDE("GUIA"),
    SERVICE_PROVIDER("PRESTADORA_SERVICIOS"),
    DESTINATION_MANAGER("GESTORA_DESTINOS"),
    ADMIN("ADMIN");

    private static final String ROLE_PREFIX = "ROLE_";

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * Resolves a role claim. Accepts the canonical Spanish code (optionally prefixed
     * with ROLE_) and, while the platform migrates, the legacy English enum name.
     * Returns null for a missing or unknown role so the caller can deny access.
     */
    public static UserRole fromClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            return null;
        }
        var normalized = claim.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(ROLE_PREFIX)) {
            normalized = normalized.substring(ROLE_PREFIX.length());
        }
        for (var role : values()) {
            if (role.code.equals(normalized) || role.name().equals(normalized)) {
                return role;
            }
        }
        return null;
    }
}
