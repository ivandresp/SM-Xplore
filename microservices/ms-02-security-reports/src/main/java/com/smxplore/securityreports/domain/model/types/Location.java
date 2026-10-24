package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidLocationParamsException;

public record Location(Double latitude, Double longitude) {
    public void validate() {
        if (latitude == null || longitude == null) {
            throw new InvalidLocationParamsException();
        }
    }
}
