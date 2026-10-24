package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;

import java.util.UUID;

public record UserRef(UUID id, String name) {
    public void validate(){
        if(id == null) throw new InvalidUserReporterException("User id is mandatory.");
        if(name == null) throw new InvalidUserReporterException("User name is mandatory.");
    }
}
