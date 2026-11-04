package com.smxplore.recommendations.application.reindex;

import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.model.reindex.ReindexResult;
import com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase;
import com.smxplore.recommendations.domain.ports.in.ReindexCatalogUseCase;
import com.smxplore.recommendations.domain.ports.out.CatalogExportPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Walks the catalog export page by page and feeds every exported event through the same projection
 * used by the Kafka consumers. The run stops when a page reports that there is nothing left, or when
 * the configured page budget is exhausted, so a broken export cannot loop forever.
 */
@RequiredArgsConstructor
public class ReindexService implements ReindexCatalogUseCase {

    private final CatalogExportPort catalogExportPort;
    private final HandleProjectionEventUseCase handleProjectionEventUseCase;
    private final int maxPages;

    @Override
    public Mono<ReindexResult> handle(CatalogEntity entity, Instant updatedSince, String bearerToken) {
        if (entity == null) {
            return Mono.error(new InvalidReindexException("entity is required"));
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            return Mono.error(new InvalidReindexException("authorization header is required"));
        }
        if (maxPages < 1) {
            return Mono.error(new InvalidReindexException("max pages must be >= 1"));
        }
        return readPage(entity, updatedSince, bearerToken, 0, Totals.empty());
    }

    private Mono<ReindexResult> readPage(
            CatalogEntity entity,
            Instant updatedSince,
            String bearerToken,
            int page,
            Totals totals
    ) {
        if (page >= maxPages) {
            return Mono.error(new InvalidReindexException(
                    "catalog export did not finish after " + maxPages + " pages"));
        }
        return catalogExportPort.fetch(bearerToken, entity, page, updatedSince)
                .flatMap(exported -> project(exported.content())
                        .flatMap(results -> {
                            Totals next = totals.plus(results);
                            return exported.hasNext()
                                    ? readPage(entity, updatedSince, bearerToken, page + 1, next)
                                    : Mono.just(next.toResult(entity, page + 1));
                        }));
    }

    private Mono<List<ProjectionResult>> project(List<ProjectedEvent> events) {
        return Flux.fromIterable(events)
                .concatMap(handleProjectionEventUseCase::handle)
                .collectList();
    }

    private record Totals(int events, int applied, int duplicate, int stale) {

        static Totals empty() {
            return new Totals(0, 0, 0, 0);
        }

        Totals plus(List<ProjectionResult> results) {
            int applied = 0;
            int duplicate = 0;
            int stale = 0;
            for (ProjectionResult result : results) {
                switch (result) {
                    case APPLIED -> applied++;
                    case DUPLICATE -> duplicate++;
                    case STALE -> stale++;
                }
            }
            return new Totals(
                    events + results.size(),
                    this.applied + applied,
                    this.duplicate + duplicate,
                    this.stale + stale);
        }

        ReindexResult toResult(CatalogEntity entity, int pages) {
            return new ReindexResult(entity, pages, events, applied, duplicate, stale);
        }
    }
}
