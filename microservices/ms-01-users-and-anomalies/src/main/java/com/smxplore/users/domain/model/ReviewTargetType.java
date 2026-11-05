package com.smxplore.users.domain.model;

import java.util.Arrays;
import java.util.List;

/**
 * Tipo de destino de una reseña dentro de ms-01. Las reseñas de atractivos, actividades, eventos y servicios
 * pertenecen a los microservicios que administran esos catálogos.
 *
 * <p>Los códigos nuevos están en inglés. Los códigos históricos se conservan para leer documentos antiguos de
 * MongoDB y clientes que todavía los envíen.
 */
public enum ReviewTargetType {
    GUIDE("GUIDE", "GUIA"),
    PROVIDER("SERVICE_PROVIDER", "PRESTADORA_SERVICIOS");

    private final String code;
    private final String legacyCode;

    ReviewTargetType(String code, String legacyCode) {
        this.code = code;
        this.legacyCode = legacyCode;
    }

    public String code() {
        return code;
    }

    public List<String> codes() {
        return List.of(code, legacyCode);
    }

    public static ReviewTargetType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code) || type.legacyCode.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de destino de reseña desconocido: " + code));
    }
}
