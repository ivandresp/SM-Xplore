package com.smxplore.users.domain.model;

import java.util.Arrays;

/**
 * Tipos de anomalía que detecta ms-01: accesos sospechosos, caídas de disponibilidad y picos anómalos de solicitudes.
 *
 * <p>Cada tipo conserva su umbral por defecto, que se usa cuando todavía no existe una configuración persistida.
 */
public enum AnomalyType {
    SUSPICIOUS_ACCESS("ACCESO_SOSPECHOSO", "Accesos sospechosos", 5, 300, AnomalySeverity.HIGH),
    SERVICE_DOWNTIME("CAIDA_SERVICIO", "Caída de servicio", 2, 60, AnomalySeverity.CRITICAL),
    REQUEST_SPIKE("PICO_SOLICITUDES", "Pico anómalo de solicitudes", 300, 60, AnomalySeverity.MEDIUM);

    private final String code;
    private final String label;
    private final int defaultThresholdValue;
    private final int defaultWindowSeconds;
    private final AnomalySeverity defaultSeverity;

    AnomalyType(
            String code,
            String label,
            int defaultThresholdValue,
            int defaultWindowSeconds,
            AnomalySeverity defaultSeverity) {
        this.code = code;
        this.label = label;
        this.defaultThresholdValue = defaultThresholdValue;
        this.defaultWindowSeconds = defaultWindowSeconds;
        this.defaultSeverity = defaultSeverity;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public int defaultThresholdValue() {
        return defaultThresholdValue;
    }

    public int defaultWindowSeconds() {
        return defaultWindowSeconds;
    }

    public AnomalySeverity defaultSeverity() {
        return defaultSeverity;
    }

    public static AnomalyType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de anomalía desconocido: " + code));
    }
}
