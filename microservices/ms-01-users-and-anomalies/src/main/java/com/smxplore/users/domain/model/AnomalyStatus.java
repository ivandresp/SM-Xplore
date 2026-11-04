package com.smxplore.users.domain.model;

import java.util.Arrays;

/**
 * Estado de atención de una anomalía. Se mantienen solo dos estados para que el panel distinga lo pendiente de lo
 * atendido sin introducir transiciones que las historias de usuario no exigen.
 */
public enum AnomalyStatus {
    DETECTED("DETECTADA"),
    RESOLVED("RESUELTA");

    private final String code;

    AnomalyStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AnomalyStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de anomalía desconocido: " + code));
    }
}
