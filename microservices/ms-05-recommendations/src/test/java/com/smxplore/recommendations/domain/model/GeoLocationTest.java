package com.smxplore.recommendations.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoLocationTest {

    @Test
    void samePointHasZeroDistance() {
        GeoLocation point = new GeoLocation(10.391, -75.479);

        assertThat(point.distanceKmTo(point)).isZero();
    }

    @Test
    void oneDegreeOfLongitudeAtEquatorIsAbout111Km() {
        double distance = new GeoLocation(0, 0).distanceKmTo(new GeoLocation(0, 1));

        assertThat(distance).isBetween(111.0, 111.4);
    }

    @Test
    void antipodalPointsDoNotProduceNaN() {
        double distance = new GeoLocation(90, 0).distanceKmTo(new GeoLocation(-90, 0));

        assertThat(distance).isNotNaN();
        assertThat(distance).isPositive();
    }
}
