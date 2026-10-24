package com.smxplore.users.identity.domain.model;

import com.smxplore.users.identity.domain.exception.ValidationException;
import java.time.LocalDate;

public record DestinationManagerProfile(Nit nit, LocalDate startOperationDate) implements Profile {

    public DestinationManagerProfile {
        if (nit == null) {
            throw new ValidationException("El NIT de la gestora de destinos es obligatorio");
        }
    }

    @Override
    public Role role() {
        return Role.GESTORA_DESTINOS;
    }
}
