package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;

public record ComponentAvailability(String name, boolean available, long latencyMs, String detail) {

    public ComponentAvailability {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre del componente es obligatorio");
        }
        if (latencyMs < 0) {
            throw new ValidationException("La latencia del componente no puede ser negativa");
        }
        detail = detail == null ? "" : detail.trim();
    }
}
