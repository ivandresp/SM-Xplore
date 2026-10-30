package com.smxplore.users.application.dto;

/**
 * Filas eliminadas por una pasada de retención, por tipo de dato.
 */
public record RetentionOutcome(long resetTokens, long loginAttempts, long recoveryCodes, long auditEntries) {

    public long total() {
        return resetTokens + loginAttempts + recoveryCodes + auditEntries;
    }
}
