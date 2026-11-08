package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidLocationParamsException;
import com.smxplore.securityreports.domain.exceptions.InvalidPageSizeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NearbyReportsQueryTests {

    @Test
    void acceptsTheMaximumRadiusAndSize() {
        assertThatCode(() -> new NearbyReportsQuery(10.0, -75.0, 20.0, null, 50))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsARadiusAboveTwentyKilometers() {
        assertThatThrownBy(() -> new NearbyReportsQuery(10.0, -75.0, 20.1, null, 20))
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("at most 20.0 km");
    }

    @Test
    void rejectsASizeAboveFifty() {
        assertThatThrownBy(() -> new NearbyReportsQuery(10.0, -75.0, 5.0, null, 51))
                .isInstanceOf(InvalidPageSizeException.class)
                .hasMessageContaining("between 1 and 50");
    }

    /** MS02-11: los parametros de /nearby llegan como {@code double}; NaN no puede llegar a geoNear. */
    @Test
    void rejectsNonFiniteQueryParameters() {
        assertThatThrownBy(() -> new NearbyReportsQuery(Double.NaN, -75.0, 5.0, null, 20))
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> new NearbyReportsQuery(10.0, Double.POSITIVE_INFINITY, 5.0, null, 20))
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> new NearbyReportsQuery(10.0, -75.0, Double.NaN, null, 20))
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
    }
}
