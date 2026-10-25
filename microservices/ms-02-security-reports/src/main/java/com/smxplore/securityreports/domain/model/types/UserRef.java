package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;

import java.util.UUID;

public record UserRef(UUID id, String name) {
    public void validate() {
        if (id == null) throw new InvalidUserReporterException("User id is mandatory.");
        if (name == null || name.isBlank()) throw new InvalidUserReporterException("User name is mandatory.");
        if (name.length() > 100) throw new InvalidUserReporterException("User name is too long.");
    }
}
