package com.smxplore.users.identity.domain.model;

import java.time.Instant;

public record AdminProfile(Dni dni, Instant lastAccessDate) implements Profile {

    @Override
    public Role role() {
        return Role.ADMIN;
    }
}
