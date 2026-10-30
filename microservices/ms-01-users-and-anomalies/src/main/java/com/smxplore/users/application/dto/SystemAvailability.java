package com.smxplore.users.application.dto;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.List;

public record SystemAvailability(boolean available, Instant checkedAt, List<ComponentAvailability> components) {

    public SystemAvailability {
        if (checkedAt == null) {
            throw new ValidationException("La fecha de verificación es obligatoria");
        }
        components = components == null ? List.of() : List.copyOf(components);
    }
}
