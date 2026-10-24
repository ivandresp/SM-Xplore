package com.smxplore.users.identity.domain.model;

import java.time.Instant;

public record AdminProfile(String dniType, String dniNumber, Instant lastAccessDate) implements Profile {

    @Override
    public Role role() {
        return Role.ADMIN;
    }
}
