package com.smxplore.activitiesevents.domain.model.user;

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

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * Resolves a role claim. Matching is exact and case-sensitive: only the canonical
     * Spanish code is accepted. The legacy English enum name is a temporary, explicit
     * migration alias until every issuer emits the Spanish codes.
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
            if (role.name().equals(claim)) {
                return role;
            }
        }
        return null;
    }
}
