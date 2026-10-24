package com.smxplore.users.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "El identificador de usuario es obligatorio");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(String value) {
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Identificador de usuario inválido", exception);
        }
    }
}
