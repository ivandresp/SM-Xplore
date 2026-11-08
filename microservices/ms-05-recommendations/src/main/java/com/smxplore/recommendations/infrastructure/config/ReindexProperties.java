package com.smxplore.recommendations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Configuration of the administrative catalog reindex.
 *
 * <p>The reindex forwards the caller's ADMIN bearer token to the catalog export of ms-04 or
 * ms-03, so MS05-03 requires three guards: the base URLs must point at internal hosts from
 * {@link #allowedHosts}, a single request may not project more than
 * {@link #maxEventsPerRequest} events, and two runs of the same administrator must be
 * separated by {@link #minInterval}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.reindex")
public class ReindexProperties {

    /**
     * Base URL of ms-04, which publishes the administrative catalog export for categories,
     * sites and services.
     */
    private String catalogBaseUrl = "http://localhost:8084";

    /**
     * Base URL of ms-03, which publishes the administrative catalog export for activities.
     * Defaults to the local ms-03 port; in Docker Compose it points at the ms-03 service.
     */
    private String activitiesBaseUrl = "http://localhost:8082";

    /**
     * Base URL of ms-03, which publishes the administrative catalog export for events.
     */
    private String eventsBaseUrl = "http://localhost:8082";

    /**
     * Hosts the three base URLs may point at. The token is only sent to one of these hosts,
     * which keeps a misconfigured environment from leaking the ADMIN bearer to an arbitrary
     * destination (MS05-03). Override with {@code REINDEX_ALLOWED_HOSTS}.
     */
    private List<String> allowedHosts = List.of(
            "localhost", "127.0.0.1", "::1", "ms-03", "ms-04", "host.docker.internal");

    private int pageSize = 200;
    private int maxPages = 500;
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Hard cap of events a single reindex request may project. The page loop stops here, so a
     * full catalog cannot monopolise the Neo4j pool in one call (MS05-03).
     */
    private int maxEventsPerRequest = 2000;

    /**
     * Minimum time between two reindex runs started by the same administrator (MS05-03).
     */
    private Duration minInterval = Duration.ofSeconds(60);

    @PostConstruct
    public void validate() {
        // The allowlist is checked first so an empty list fails with its own message instead of
        // surfacing as "host is not in app.reindex.allowed-hosts" for every base URL.
        validateAllowedHosts();
        validateBaseUrl("catalog-base-url", catalogBaseUrl);
        validateBaseUrl("activities-base-url", activitiesBaseUrl);
        validateBaseUrl("events-base-url", eventsBaseUrl);
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalStateException("app.reindex.page-size must be between 1 and 200");
        }
        if (maxPages < 1) {
            throw new IllegalStateException("app.reindex.max-pages must be >= 1");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("app.reindex.timeout must be positive");
        }
        int pageCapacity = pageSize * maxPages;
        if (maxEventsPerRequest < 1 || maxEventsPerRequest > pageCapacity) {
            throw new IllegalStateException(
                    "app.reindex.max-events-per-request must be between 1 and " + pageCapacity);
        }
        if (minInterval == null || minInterval.isNegative()) {
            throw new IllegalStateException("app.reindex.min-interval must be >= 0");
        }
    }

    /**
     * @return {@code true} when the host is one of the configured internal hosts
     */
    public boolean isAllowedHost(String host) {
        String normalized = normalizeHost(host);
        return normalized != null && allowedHostSet().contains(normalized);
    }

    private java.util.Set<String> allowedHostSet() {
        return allowedHosts == null
                ? java.util.Set.of()
                : allowedHosts.stream()
                        .map(this::normalizeHost)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private void validateAllowedHosts() {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new IllegalStateException("app.reindex.allowed-hosts must not be empty");
        }
        for (String host : allowedHosts) {
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("app.reindex.allowed-hosts must not contain blank hosts");
            }
        }
    }

    private void validateBaseUrl(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.reindex." + property + " is required");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "app.reindex." + property + " must be an absolute http(s) URL", exception);
        }
        if (uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalStateException(
                    "app.reindex." + property + " must be an absolute http(s) URL");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException(
                    "app.reindex." + property + " must not contain credentials");
        }
        if (!isAllowedHost(uri.getHost())) {
            throw new IllegalStateException(
                    "app.reindex." + property + " host " + uri.getHost()
                            + " is not in app.reindex.allowed-hosts");
        }
    }
}
