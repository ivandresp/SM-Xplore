package com.smxplore.activitiesevents.infrastructure.adapters.out.catalog;

import com.smxplore.activitiesevents.domain.exceptions.catalog.SiteCatalogUnavailableException;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Reads a site snapshot from ms-04 over HTTP. The site endpoint is the source of truth for the
 * site name, zone and manager of the event/activity demand indicators.
 *
 * <p>Los listados se leen pagina a pagina con {@code totalPages}. Una respuesta anomal a de ms-04
 * (o una pagina que cambia de tamano entre peticiones) no puede encadenar peticiones sin fin ni
 * cargar todo en memoria: {@code app.ms04.max-pages} acota la lectura y, si se supera, se falla con
 * un {@code 503} controlado.
 */
@Component
public class Ms04SiteCatalogAdapter implements SiteCatalogPort {

    private static final int PAGE_SIZE = 100;

    /** Tope por defecto: 100 paginas x 100 sitios = 10 000 sitios por gestora. */
    static final int DEFAULT_MAX_PAGES = 100;

    private final WebClient webClient;
    private final Duration timeout;
    private final int maxPages;

    @Autowired
    public Ms04SiteCatalogAdapter(@Value("${app.ms04.base-url}") String baseUrl,
                                  @Value("${app.ms04.timeout}") Duration timeout,
                                  @Value("${app.ms04.max-pages:100}") int maxPages) {
        this(WebClient.builder().baseUrl(baseUrl).build(), timeout, maxPages);
    }

    /** Constructor para pruebas: mismo comportamiento sin arrancar Spring ni un servidor real. */
    Ms04SiteCatalogAdapter(WebClient webClient, Duration timeout, int maxPages) {
        this.webClient = webClient;
        this.timeout = timeout;
        this.maxPages = Math.max(1, maxPages);
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
                .map(this::snapshot);
    }

    @Override
    public Flux<SiteSnapshot> findByManagerId(UUID managerId, String bearerToken) {
        return page(uriBuilder -> uriBuilder.path("/api/v1/sites")
                .queryParam("managerId", managerId), bearerToken);
    }

    @Override
    public Flux<SiteSnapshot> findByZoneId(UUID zoneId, String bearerToken) {
        return page(uriBuilder -> uriBuilder.path("/api/v1/sites")
                .queryParam("zoneId", zoneId), bearerToken);
    }

    private Flux<SiteSnapshot> page(java.util.function.Consumer<org.springframework.web.util.UriBuilder> path,
                                    String bearerToken) {
        return page(path, 0, bearerToken);
    }

    private Flux<SiteSnapshot> page(
            java.util.function.Consumer<org.springframework.web.util.UriBuilder> path,
            int page,
            String bearerToken) {
        return webClient.get()
                .uri(uriBuilder -> {
                    path.accept(uriBuilder);
                    return uriBuilder.queryParam("page", page)
                            .queryParam("size", PAGE_SIZE)
                            .build();
                })
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(SitePage.class)
                .timeout(timeout)
                .flatMapMany(result -> {
                    var current = Flux.fromIterable(result.content()).map(this::snapshot);
                    if (page + 1 >= result.totalPages()) {
                        return current;
                    }
                    if (page + 1 >= maxPages) {
                        // totalPages anomalo o crecimiento entre paginas: no se encadena sin fin.
                        return Flux.error(new SiteCatalogUnavailableException(
                                ("The site catalog returned more than %d pages (%d); pagination is"
                                        + " capped to protect the request.").formatted(maxPages, result.totalPages())));
                    }
                    return current.concatWith(page(path, page + 1, bearerToken));
                });
    }

    private SiteSnapshot snapshot(SiteResponse site) {
        return new SiteSnapshot(site.siteId(), site.managerId(), site.name(), site.zoneId(),
                site.zoneName(), site.zoneStatus());
    }

    private record SitePage(List<SiteResponse> content, int page, int size, long total, int totalPages) {
    }

    private record SiteResponse(
            UUID siteId,
            UUID managerId,
            String name,
            UUID zoneId,
            String zoneName,
            String zoneStatus
    ) {
    }
}
