package com.smxplore.users.domain.model;

import java.util.Arrays;

/**
 * Severidad de una anomalía. El nombre de la constante es técnico; el código es el valor que viaja por la API y la
 * base de datos.
 */
public enum AnomalySeverity {
    LOW("BAJA"),
    MEDIUM("MEDIA"),
    HIGH("ALTA"),
    CRITICAL("CRITICA");

    private final String code;

    AnomalySeverity(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public int priority() {
        return switch (this) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    public static AnomalySeverity fromCode(String code) {
        return Arrays.stream(values())
                .filter(severity -> severity.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Severidad de anomalía desconocida: " + code));
    }
}
