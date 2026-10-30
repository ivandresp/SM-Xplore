package com.smxplore.users.domain.model;

import java.util.Arrays;

/**
 * Tipo de destino de una reseña dentro de ms-01. Las reseñas de atractivos, actividades, eventos y servicios
 * pertenecen a los microservicios que administran esos catálogos.
 */
public enum ReviewTargetType {
    GUIDE("GUIA"),
    PROVIDER("PRESTADORA_SERVICIOS");

    private final String code;

    ReviewTargetType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReviewTargetType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de destino de reseña desconocido: " + code));
    }
}
