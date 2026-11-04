package com.smxplore.activitiesevents.infrastructure.adapters.out.catalog;

import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Reads a site snapshot from ms-04 over HTTP. The endpoint is the public
 * GET /api/v1/sites/{siteId}; ms-03 does not own site data.
 */
@Component
public class Ms04SiteCatalogAdapter implements SiteCatalogPort {

    private static final int PAGE_SIZE = 100;

    private final WebClient webClient;
    private final Duration timeout;

    public Ms04SiteCatalogAdapter(@Value("${app.ms04.base-url}") String baseUrl,
                                  @Value("${app.ms04.timeout}") Duration timeout) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeout = timeout;
    }

    @Override
    public Mono<SiteSnapshot> findById(UUID siteId, String bearerToken) {
        return webClient.get()
                .uri("/api/v1/sites/{siteId}", siteId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(SiteResponse.class)
                .timeout(timeout)
                .onErrorResume(WebClientResponseException.NotFound.class, exception -> Mono.empty())
                .map(site -> new SiteSnapshot(site.siteId(), site.managerId(), site.name()));
    }

    @Override
    public Flux<SiteSnapshot> findByManagerId(UUID managerId, String bearerToken) {
        return page(managerId, 0, bearerToken);
    }

    private Flux<SiteSnapshot> page(UUID managerId, int page, String bearerToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/sites")
                        .queryParam("managerId", managerId)
                        .queryParam("page", page)
                        .queryParam("size", PAGE_SIZE)
                        .build())
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(SitePage.class)
                .timeout(timeout)
                .flatMapMany(result -> {
                    var current = Flux.fromIterable(result.content())
                            .map(site -> new SiteSnapshot(site.siteId(), site.managerId(), site.name()));
                    return page + 1 < result.totalPages()
                            ? current.concatWith(page(managerId, page + 1, bearerToken))
                            : current;
                });
    }

    private record SitePage(List<SiteResponse> content, int page, int size, long total, int totalPages) {
    }

    private record SiteResponse(UUID siteId, UUID managerId, String name) {
    }
}
