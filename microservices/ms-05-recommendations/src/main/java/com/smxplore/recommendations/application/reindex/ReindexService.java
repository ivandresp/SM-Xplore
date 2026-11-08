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
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Walks the catalog export page by page and feeds every exported event through the same projection
 * used by the Kafka consumers. The run stops when a page reports that there is nothing left, when
 * the configured page budget is exhausted, or when the event cap is reached, so a broken export
 * cannot loop forever or write an unbounded amount of graph data in one call.
 *
 * <p>The reindex forwards the administrator bearer token to ms-04/ms-03, so it also applies a
 * per-administrator throttle and writes an audit line with the actor and the outcome (MS05-03).</p>
 */
@Slf4j
@RequiredArgsConstructor
public class ReindexService implements ReindexCatalogUseCase {

    private static final int MAX_TRACKED_ACTORS = 10_000;

    private final CatalogExportPort catalogExportPort;
    private final HandleProjectionEventUseCase handleProjectionEventUseCase;
    private final int maxPages;
    private final int maxEventsPerRequest;
    private final Duration minInterval;
    private final Clock clock;
    private final Map<UUID, Instant> lastRunByActor = new ConcurrentHashMap<>();

    @Override
    public Mono<ReindexResult> handle(CatalogEntity entity, Instant updatedSince, String bearerToken,
                                      UUID actorId) {
        if (entity == null) {
            return Mono.error(new InvalidReindexException("entity is required"));
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            return Mono.error(new InvalidReindexException("authorization header is required"));
        }
        if (actorId == null) {
            return Mono.error(new InvalidReindexException("reindex actor is required"));
        }
        if (maxPages < 1) {
            return Mono.error(new InvalidReindexException("max pages must be >= 1"));
        }
        if (maxEventsPerRequest < 1) {
            return Mono.error(new InvalidReindexException("max events per request must be >= 1"));
        }
        Instant now = clock.instant();
        String throttleError = throttleError(actorId, now);
        if (throttleError != null) {
            return Mono.error(new InvalidReindexException(throttleError));
        }
        log.info("reindex started actor={} entity={} updatedSince={} maxPages={} maxEvents={}",
                actorId, entity, updatedSince, maxPages, maxEventsPerRequest);
        return readPage(entity, updatedSince, bearerToken, 0, Totals.empty())
                .doOnSuccess(result -> log.info(
                        "reindex completed actor={} entity={} pages={} events={} applied={} duplicate={} stale={} durationMs={}",
                        actorId, result.entity(), result.pages(), result.events(), result.applied(),
                        result.duplicate(), result.stale(),
                        Duration.between(now, clock.instant()).toMillis()))
                .doOnError(error -> log.warn("reindex failed actor={} entity={} error={}",
                        actorId, entity, error.getMessage()));
    }

    private String throttleError(UUID actorId, Instant now) {
        Instant last = lastRunByActor.get(actorId);
        if (last != null && Duration.between(last, now).compareTo(minInterval) < 0) {
            long waitSeconds = Math.max(1, minInterval.minus(Duration.between(last, now)).toSeconds() + 1);
            return "a reindex was started less than " + minInterval.toSeconds()
                    + " seconds ago; wait " + waitSeconds + " seconds before retrying";
        }
        lastRunByActor.put(actorId, now);
        if (lastRunByActor.size() > MAX_TRACKED_ACTORS) {
            lastRunByActor.entrySet().removeIf(entry ->
                    Duration.between(entry.getValue(), now).compareTo(minInterval.multipliedBy(2)) > 0);
        }
        return null;
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
                            if (next.events() > maxEventsPerRequest) {
                                return Mono.error(new InvalidReindexException(
                                        "reindex projected more than " + maxEventsPerRequest
                                                + " events; narrow the updatedSince window and retry"));
                            }
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
