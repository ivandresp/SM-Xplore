package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.reindex")
public class ReindexProperties {

    /**
     * Base URL of ms-04, which publishes the administrative catalog export.
     */
    private String catalogBaseUrl = "http://localhost:8084";
    private int pageSize = 200;
    private int maxPages = 500;
    private Duration timeout = Duration.ofSeconds(30);

    @PostConstruct
    public void validate() {
        if (catalogBaseUrl == null || catalogBaseUrl.isBlank()) {
            throw new IllegalStateException("app.reindex.catalog-base-url is required");
        }
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalStateException("app.reindex.page-size must be between 1 and 200");
        }
        if (maxPages < 1) {
            throw new IllegalStateException("app.reindex.max-pages must be >= 1");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("app.reindex.timeout must be positive");
        }
    }
}
