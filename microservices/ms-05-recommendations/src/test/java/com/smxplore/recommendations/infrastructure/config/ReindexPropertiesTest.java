package com.smxplore.recommendations.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for MS05-03: the ADMIN bearer may only be sent to internal hosts, a single
 * request cannot project the whole catalog and an administrator cannot restart the reindex in
 * a tight loop.
 */
class ReindexPropertiesTest {

    @Test
    void acceptsTheDefaultConfiguration() {
        ReindexProperties properties = new ReindexProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.isAllowedHost("127.0.0.1")).isTrue();
        assertThat(properties.isAllowedHost("MS-04")).isTrue();
        assertThat(properties.isAllowedHost("[::1]")).isTrue();
        assertThat(properties.isAllowedHost("evil.example")).isFalse();
    }

    @Test
    void rejectsABaseUrlOutsideTheAllowlist() {
        ReindexProperties properties = new ReindexProperties();
        properties.setCatalogBaseUrl("http://evil.example:8080");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.reindex.catalog-base-url")
                .hasMessageContaining("evil.example")
                .hasMessageContaining("app.reindex.allowed-hosts");
    }

    @Test
    void rejectsABaseUrlWithCredentials() {
        ReindexProperties properties = new ReindexProperties();
        properties.setActivitiesBaseUrl("http://user:secret@ms-03:8082");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.reindex.activities-base-url")
                .hasMessageContaining("must not contain credentials");
    }

    @Test
    void rejectsANonHttpBaseUrl() {
        ReindexProperties properties = new ReindexProperties();
        properties.setEventsBaseUrl("file:///etc/passwd");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.reindex.events-base-url")
                .hasMessageContaining("must be an absolute http(s) URL");
    }

    @Test
    void rejectsAnEmptyAllowlist() {
        ReindexProperties properties = new ReindexProperties();
        properties.setAllowedHosts(List.of());

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.reindex.allowed-hosts must not be empty");
    }

    @Test
    void rejectsMoreEventsThanThePageWindowCanHold() {
        ReindexProperties properties = new ReindexProperties();
        properties.setMaxEventsPerRequest(properties.getPageSize() * properties.getMaxPages() + 1);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.reindex.max-events-per-request must be between 1 and");
    }

    @Test
    void rejectsANegativeMinimumInterval() {
        ReindexProperties properties = new ReindexProperties();
        properties.setMinInterval(Duration.ofSeconds(-1));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.reindex.min-interval must be >= 0");
    }
}
