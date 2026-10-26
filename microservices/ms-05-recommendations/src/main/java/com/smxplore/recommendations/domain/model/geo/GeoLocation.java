package com.smxplore.recommendations.domain.model.geo;

import com.smxplore.recommendations.domain.exception.InvalidGeoLocationException;

import java.util.Objects;

/**
 * WGS84 coordinate. Distance is always calculated at query time; it is never
 * persisted as a relationship property.
 */
public record GeoLocation(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    public GeoLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        validate();
    }

    /**
     * Public validation entry point. It only delegates to small, named checks.
     */
    public void validate() {
        validateLatitude();
        validateLongitude();
    }

    public double distanceKmTo(GeoLocation other) {
        Objects.requireNonNull(other, "other location is required");

        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - latitude);
        double deltaLon = Math.toRadians(other.longitude - longitude);

        double a = Math.pow(Math.sin(deltaLat / 2.0), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2.0), 2);
        a = Math.clamp(a, 0.0, 1.0);
        return 2.0 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    private void validateLatitude() {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new InvalidGeoLocationException("latitude must be between -90 and 90");
        }
    }

    private void validateLongitude() {
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new InvalidGeoLocationException("longitude must be between -180 and 180");
        }
    }
}
