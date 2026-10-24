package com.smxplore.users.identity.domain.model;

import java.time.LocalDate;

public record DestinationManagerProfile(String nit, LocalDate startOperationDate) implements Profile {

    @Override
    public Role role() {
        return Role.GESTORA_DESTINOS;
    }
}
