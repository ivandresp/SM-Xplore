package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReviewResponse(UUID responderId, String text, Instant createdAt, Instant updatedAt) {

    public ReviewResponse {
        Objects.requireNonNull(responderId, "El autor de la respuesta es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de la respuesta es obligatoria");
        Objects.requireNonNull(updatedAt, "La fecha de actualización de la respuesta es obligatoria");
        if (text == null || text.isBlank()) {
            throw new ValidationException("La respuesta es obligatoria");
        }
        text = text.trim();
        if (text.length() > 1000) {
            throw new ValidationException("La respuesta no puede superar los 1000 caracteres");
        }
    }
}
