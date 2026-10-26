package com.smxplore.users.domain.model;

import java.util.Arrays;

/**
 * Roles de la plataforma.
 *
 * <p>Los identificadores Java están en inglés; el código expuesto en la API, el JWT y la base de datos se mantiene en
 * español para no romper el contrato con los demás microservicios ni los datos existentes.
 */
public enum Role {
    TOURIST("TURISTA"),
    GUIDE("GUIA"),
    SERVICE_PROVIDER("PRESTADORA_SERVICIOS"),
    DESTINATION_MANAGER("GESTORA_DESTINOS"),
    ADMIN("ADMIN");

    private final String code;

    Role(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Role fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rol desconocido: " + code));
    }
}
