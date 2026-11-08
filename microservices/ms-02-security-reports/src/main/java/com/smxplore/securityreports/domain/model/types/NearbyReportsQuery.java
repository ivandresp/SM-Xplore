package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidLocationParamsException;
import com.smxplore.securityreports.domain.exceptions.InvalidPageSizeException;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;

public record NearbyReportsQuery(
        double latitude,
        double longitude,
        double radiusKm,
        SecurityReportStatus status,
        int limit
) {

    public static final double MAX_RADIUS_KM = 20.0;
    public static final int MAX_LIMIT = 50;

    public NearbyReportsQuery {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new InvalidLocationParamsException("Latitude must be between -90 and 90.");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new InvalidLocationParamsException("Longitude must be between -180 and 180.");
        }
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidLocationParamsException(
                    "Radius must be greater than 0 and at most " + MAX_RADIUS_KM + " km.");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidPageSizeException(
                    "Limit must be between 1 and " + MAX_LIMIT + ".");
        }
    }
}
