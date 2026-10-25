package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidLocationParamsException;

public record Location(Double latitude, Double longitude) {
    public void validate() {
        if (latitude == null || longitude == null) {
            throw new InvalidLocationParamsException();
        }
        validateLatitude(latitude);
        validateLongitude(longitude);
    }

    private void validateLatitude(Double latitude){
        if (latitude < -90.0 || latitude > 90.0) {
            throw new InvalidLocationParamsException("Latitude must be between -90 and 90.");
        }
    }

    private void validateLongitude(Double longitude){
        if (longitude < -180.0 || longitude > 180.0) {
            throw new InvalidLocationParamsException("Longitude must be between -180 and 180.");
        }
    }
}
