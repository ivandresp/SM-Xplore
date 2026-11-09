package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidLocationParamsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS02-11: las coordenadas del dominio deben rechazar NaN/Infinity, no solo valores fuera de rango.
 */
class LocationTests {

    @Test
    void acceptsAValidCoordinate() {
        assertThatCode(() -> new Location(10.3916, -75.4786).validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsANonFiniteLatitude() {
        assertThatThrownBy(() -> new Location(Double.NaN, -75.4786).validate())
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> new Location(Double.POSITIVE_INFINITY, -75.4786).validate())
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
    }

    @Test
    void rejectsANonFiniteLongitude() {
        assertThatThrownBy(() -> new Location(10.3916, Double.NaN).validate())
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> new Location(10.3916, Double.NEGATIVE_INFINITY).validate())
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("finite");
    }

    @Test
    void stillRejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new Location(90.1, -75.4786).validate())
                .isInstanceOf(InvalidLocationParamsException.class)
                .hasMessageContaining("between -90 and 90");
    }
}
