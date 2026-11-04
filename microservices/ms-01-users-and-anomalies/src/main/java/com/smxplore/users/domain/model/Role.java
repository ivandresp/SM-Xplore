package com.smxplore.users.domain.model;

import java.util.Arrays;

/**
 * Roles de la plataforma.
 *
 * <p>Los identificadores Java y los códigos expuestos en la API, el JWT y la base de datos están en inglés.
 * Los códigos antiguos en español se aceptan solo para leer datos históricos durante la migración.
 */
public enum Role {
    TOURIST("TOURIST", "TURISTA"),
    GUIDE("GUIDE", "GUIA"),
    SERVICE_PROVIDER("SERVICE_PROVIDER", "PRESTADORA_SERVICIOS"),
    DESTINATION_MANAGER("DESTINATION_MANAGER", "GESTORA_DESTINOS"),
    ADMIN("ADMIN", "ADMIN");

    private final String code;
    private final String legacyCode;

    Role(String code, String legacyCode) {
        this.code = code;
        this.legacyCode = legacyCode;
    }

    public String code() {
        return code;
    }

    public static Role fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code) || role.legacyCode.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rol desconocido: " + code));
    }
}
