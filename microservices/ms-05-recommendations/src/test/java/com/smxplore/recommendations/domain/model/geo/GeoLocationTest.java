package com.smxplore.recommendations.domain.model.geo;

import com.smxplore.recommendations.domain.exception.InvalidGeoLocationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsInvalidLatitude() {
        assertThatThrownBy(() -> new GeoLocation(91, 0))
                .isInstanceOf(InvalidGeoLocationException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void rejectsInvalidLongitude() {
        assertThatThrownBy(() -> new GeoLocation(0, -181))
                .isInstanceOf(InvalidGeoLocationException.class)
                .hasMessageContaining("longitude");
    }
}
