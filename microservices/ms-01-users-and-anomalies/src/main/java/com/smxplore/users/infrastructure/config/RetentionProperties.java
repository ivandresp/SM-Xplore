package com.smxplore.users.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Política de retención de datos (Ley 1581 de 2012). Los valores por defecto conservan la auditoría cinco años y
 * limpian los artefactos de autenticación a los 30 días.
 */
@ConfigurationProperties("app.retention")
public record RetentionProperties(boolean enabled, int authArtifactsDays, int auditLogDays) {

    public RetentionProperties {
        if (authArtifactsDays < 1) {
            authArtifactsDays = 30;
        }
        if (auditLogDays < 1) {
            auditLogDays = 1825;
        }
    }
}
