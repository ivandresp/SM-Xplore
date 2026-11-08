package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS04-06: el filtro de precio y la paginacion se validan con criterios tipados antes de llegar
 * a Mongo.
 */
class ServiceFilterValidationTest {

    @Test
    void acceptsAValidPriceRange() {
        assertThatCode(() -> filter(new BigDecimal("10"), new BigDecimal("50"), 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativePrices() {
        assertThatThrownBy(() -> filter(new BigDecimal("-1"), null, 0, 20))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("priceMin must be >= 0");
        assertThatThrownBy(() -> filter(null, new BigDecimal("-5"), 0, 20))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("priceMax must be >= 0");
    }

    @Test
    void rejectsPricesAboveTheCap() {
        assertThatThrownBy(() -> filter(new BigDecimal("100000001"), null, 0, 20))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("priceMin must be <= 100000000");
    }

    @Test
    void rejectsInvertedRanges() {
        assertThatThrownBy(() -> filter(new BigDecimal("50"), new BigDecimal("10"), 0, 20))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("priceMin must be <= priceMax");
    }

    @Test
    void rejectsOffsetsThatAreTooLarge() {
        assertThatThrownBy(() -> filter(null, null, 1000, 100))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("page and size are too large");
    }

    private static ServiceFilter filter(BigDecimal min, BigDecimal max, int page, int size) {
        return new ServiceFilter(
                null, null, null, null, null, null, null, min, max, page, size);
    }
}
