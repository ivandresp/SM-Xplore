package com.smxplore.activitiesevents.infrastructure.adapters.out.catalog;

import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Reads a site snapshot from ms-04 over HTTP. The endpoint is the public
 * GET /api/v1/sites/{siteId}; ms-03 does not own site data.
 */
@Component
public class Ms04SiteCatalogAdapter implements SiteCatalogPort {

    private final WebClient webClient;
    private final Duration timeout;

    public Ms04SiteCatalogAdapter(@Value("${app.ms04.base-url}") String baseUrl,
                                  @Value("${app.ms04.timeout}") Duration timeout) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeout = timeout;
    }

    @Override
    public Mono<SiteSnapshot> findById(UUID siteId) {
        return webClient.get()
                .uri("/api/v1/sites/{siteId}", siteId)
                .retrieve()
                .bodyToMono(SiteResponse.class)
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.NotFound.class, exception -> Mono.empty())
                .map(site -> new SiteSnapshot(site.siteId(), site.managerId(), site.name()));
    }

    private record SiteResponse(UUID siteId, UUID managerId, String name) {
    }
}
