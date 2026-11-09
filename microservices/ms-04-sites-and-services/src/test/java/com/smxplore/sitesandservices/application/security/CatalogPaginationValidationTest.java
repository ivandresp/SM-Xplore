package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cota de desplazamiento en los listados publicos de ms-04 (auditoria final 2026-11-08):
 * sin ella un anonimo puede pedir un offset arbitrario y forzar un count(*) caro con cada
 * peticion. SiteFilter, CategoryFilter, ZoneFilter y ReviewFilter aplican la misma cota que
 * ServiceFilter desde MS04-06.
 */
class CatalogPaginationValidationTest {

    @Test
    void acceptsPagesWithinTheOffsetCap() {
        assertThatCode(() -> new SiteFilter(null, null, null, null, null, 0, 20))
                .doesNotThrowAnyException();
        assertThatCode(() -> new SiteFilter(null, null, null, null, null, 100, 100))
                .doesNotThrowAnyException();
        assertThatCode(() -> new CategoryFilter(null, null, null, 0, 50))
                .doesNotThrowAnyException();
        assertThatCode(() -> new ZoneFilter(null, null, null, 0, 50))
                .doesNotThrowAnyException();
        assertThatCode(() -> new ReviewFilter(null, null, null, null, 0, 50))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSitePagesBeyondTheOffsetCap() {
        assertThatThrownBy(() -> new SiteFilter(null, null, null, null, null, 101, 100))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("page and size are too large");
    }

    @Test
    void rejectsCategoryPagesBeyondTheOffsetCap() {
        assertThatThrownBy(() -> new CategoryFilter(null, null, null, 1001, 10))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("page and size are too large");
    }

    @Test
    void rejectsZonePagesBeyondTheOffsetCap() {
        assertThatThrownBy(() -> new ZoneFilter(null, null, null, 1001, 10))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("page and size are too large");
    }

    @Test
    void rejectsReviewPagesBeyondTheOffsetCap() {
        assertThatThrownBy(() -> new ReviewFilter(null, null, null, null, 1001, 10))
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("page and size are too large");
    }
}
