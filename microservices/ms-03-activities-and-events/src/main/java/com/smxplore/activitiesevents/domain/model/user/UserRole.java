package com.smxplore.activitiesevents.domain.model.user;

/**
 * Platform roles.
 *
 * <p>Java identifiers and the wire value carried by the JWT use the English codes published by
 * ms-01 ({@code TOURIST}, {@code GUIDE}, {@code SERVICE_PROVIDER}, {@code DESTINATION_MANAGER},
 * {@code ADMIN}). The former Spanish codes are accepted only as read-only aliases so tokens
 * issued before the ms-01 {@code V10__english_role_codes} migration keep working until they
 * expire.
 */
public enum UserRole {

    TOURIST("TOURIST", "TURISTA"),
    GUIDE("GUIDE", "GUIA"),
    SERVICE_PROVIDER("SERVICE_PROVIDER", "PRESTADORA_SERVICIOS"),
    DESTINATION_MANAGER("DESTINATION_MANAGER", "GESTORA_DESTINOS"),
    ADMIN("ADMIN", "ADMIN");

    private final String code;
    private final String legacyCode;

    UserRole(String code, String legacyCode) {
        this.code = code;
        this.legacyCode = legacyCode;
    }

    /**
     * @return the canonical English code emitted by ms-01 in the {@code role} claim.
     */
    public String code() {
        return code;
    }

    /**
     * @return the Spanish code used before the ms-01 role migration. Never emit it: it exists
     *     only to keep already issued tokens readable.
     */
    public String legacyCode() {
        return legacyCode;
    }

    /**
     * Resolves a role claim. Matching is exact and case-sensitive: the canonical English code is
     * accepted first, then the legacy Spanish alias. Anything else yields {@code null}.
     *
     * @return the role, or null when the claim is missing or unknown
     */
    public static UserRole fromClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            return null;
        }
        for (var role : values()) {
            if (role.code.equals(claim)) {
                return role;
            }
        }
        for (var role : values()) {
            if (role.legacyCode.equals(claim)) {
                return role;
            }
        }
        return null;
    }
}
