package com.smxplore.recommendations.application.reindex;

import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.model.reindex.CatalogExportPage;
import com.smxplore.recommendations.domain.model.reindex.ReindexResult;
import com.smxplore.recommendations.domain.ports.out.CatalogExportPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ReindexServiceTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-11-02T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(UPDATED_AT, ZoneOffset.UTC);
    private static final String TOKEN = "Bearer admin-token";
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void stopsWhenTheLastPageHasNoNextPage() {
        FakeCatalogExportPort port = new FakeCatalogExportPort(List.of(
                new CatalogExportPage(List.of(event(), event()), 0, 2, 3, true),
                new CatalogExportPage(List.of(event()), 1, 2, 3, false)));
        FakeProjection projection = new FakeProjection(ProjectionResult.APPLIED);
        ReindexService service = service(port, projection, 10);

        ReindexResult result = service.handle(CatalogEntity.SITES, null, TOKEN, ACTOR).block();

        assertThat(result).isNotNull();
        assertThat(result.entity()).isEqualTo(CatalogEntity.SITES);
        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.events()).isEqualTo(3);
        assertThat(result.applied()).isEqualTo(3);
        assertThat(result.duplicate()).isZero();
        assertThat(result.stale()).isZero();
        assertThat(port.pages()).containsExactly(0, 1);
        assertThat(port.tokens()).containsOnly(TOKEN);
    }

    @Test
    void forwardsTheIncrementalFilterAndTheCallerToken() {
        FakeCatalogExportPort port = new FakeCatalogExportPort(List.of(
                new CatalogExportPage(List.of(event()), 0, 200, 1, false)));
        ReindexService service = service(port, new FakeProjection(ProjectionResult.APPLIED), 10);

        service.handle(CatalogEntity.SERVICES, UPDATED_AT, TOKEN, ACTOR).block();

        assertThat(port.updatedSince()).containsExactly(UPDATED_AT);
        assertThat(port.entities()).containsOnly(CatalogEntity.SERVICES);
    }

    @Test
    void countsDuplicatesAndStaleEvents() {
        FakeCatalogExportPort port = new FakeCatalogExportPort(List.of(
                new CatalogExportPage(List.of(event(), event(), event()), 0, 3, 3, false)));
        FakeProjection projection = new FakeProjection(
                ProjectionResult.APPLIED, ProjectionResult.DUPLICATE, ProjectionResult.STALE);
        ReindexService service = service(port, projection, 10);

        ReindexResult result = service.handle(CatalogEntity.CATEGORIES, null, TOKEN, ACTOR).block();

        assertThat(result).isNotNull();
        assertThat(result.events()).isEqualTo(3);
        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.duplicate()).isEqualTo(1);
        assertThat(result.stale()).isEqualTo(1);
    }

    @Test
    void refusesToLoopForeverWhenTheExportNeverFinishes() {
        FakeCatalogExportPort port = new FakeCatalogExportPort(List.of(
                new CatalogExportPage(List.of(event()), 0, 1, 1, true),
                new CatalogExportPage(List.of(event()), 1, 1, 1, true),
                new CatalogExportPage(List.of(event()), 2, 1, 1, true)));
        ReindexService service = service(port, new FakeProjection(ProjectionResult.APPLIED), 2);

        StepVerifier.create(service.handle(CatalogEntity.SITES, null, TOKEN, ACTOR))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("did not finish after 2 pages"))
                .verify();
    }

    @Test
    void rejectsRequestsWithoutEntityOrToken() {
        ReindexService service = service(
                new FakeCatalogExportPort(List.of()), new FakeProjection(ProjectionResult.APPLIED), 10);

        StepVerifier.create(service.handle(null, null, TOKEN, ACTOR))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("entity is required"))
                .verify();
        StepVerifier.create(service.handle(CatalogEntity.SITES, null, " ", ACTOR))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("authorization header is required"))
                .verify();
    }

    /** MS05-03: the run is auditable, so an anonymous actor is rejected. */
    @Test
    void rejectsAReindexWithoutAnActor() {
        ReindexService service = service(
                new FakeCatalogExportPort(List.of()), new FakeProjection(ProjectionResult.APPLIED), 10);

        StepVerifier.create(service.handle(CatalogEntity.SITES, null, TOKEN, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("actor is required"))
                .verify();
    }

    /** MS05-03: one administrator cannot restart the reindex in a tight loop. */
    @Test
    void throttlesTwoReindexesFromTheSameAdministrator() {
        FakeCatalogExportPort port = new FakeCatalogExportPort(List.of(
                new CatalogExportPage(List.of(event()), 0, 1, 1, false)));
        ReindexService service = new ReindexService(port, new FakeProjection(ProjectionResult.APPLIED),
                10, 2000, Duration.ofSeconds(60), CLOCK);

        assertThat(service.handle(CatalogEntity.SITES, null, TOKEN, ACTOR).block()).isNotNull();

        StepVerifier.create(service.handle(CatalogEntity.SITES, null, TOKEN, ACTOR))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("wait"))
                .verify();
        assertThat(port.pages()).hasSize(1);
    }

    /** MS05-03: a single request cannot project the whole catalog. */
    @Test
    void enforcesTheMaximumNumberOfEventsPerRequest() {
        FakeCatalogExportPort port = new FakeCatalogExportPort(List.of(
                new CatalogExportPage(List.of(event(), event()), 0, 4, 1, true),
                new CatalogExportPage(List.of(event(), event()), 1, 4, 1, false)));
        ReindexService service = new ReindexService(port, new FakeProjection(ProjectionResult.APPLIED),
                10, 3, Duration.ZERO, CLOCK);

        StepVerifier.create(service.handle(CatalogEntity.SITES, null, TOKEN, ACTOR))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidReindexException.class)
                        .hasMessageContaining("more than 3 events"))
                .verify();
    }

    private static ReindexService service(CatalogExportPort port,
                                          com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase projection,
                                          int maxPages) {
        return new ReindexService(port, projection, maxPages, 2000, Duration.ZERO, CLOCK);
    }

    private static com.smxplore.recommendations.domain.model.event.ProjectedEvent event() {
        UUID siteId = UUID.randomUUID();
        return new com.smxplore.recommendations.domain.model.event.SiteChangedEvent(
                new com.smxplore.recommendations.domain.model.event.EventMetadata(
                        UUID.randomUUID(),
                        com.smxplore.recommendations.domain.model.event.EventTopics.CATALOG_SITE_CHANGED,
                        1,
                        UPDATED_AT,
                        siteId,
                        "ms-05-reindex",
                        "ms-04"),
                siteId,
                "Sitio de prueba",
                UUID.randomUUID(),
                10.4,
                -75.5,
                com.smxplore.recommendations.domain.model.catalog.ContentStatus.PUBLISHED,
                UPDATED_AT);
    }

    private static final class FakeCatalogExportPort implements CatalogExportPort {

        private final List<CatalogExportPage> pages;
        private final List<Integer> requestedPages = new CopyOnWriteArrayList<>();
        private final List<CatalogEntity> entities = new CopyOnWriteArrayList<>();
        private final List<Instant> updatedSince = new CopyOnWriteArrayList<>();
        private final List<String> tokens = new CopyOnWriteArrayList<>();

        private FakeCatalogExportPort(List<CatalogExportPage> pages) {
            this.pages = pages;
        }

        @Override
        public Mono<CatalogExportPage> fetch(String bearerToken, CatalogEntity entity, int page,
                                             Instant updatedSince) {
            requestedPages.add(page);
            entities.add(entity);
            this.updatedSince.add(updatedSince);
            tokens.add(bearerToken);
            Optional<CatalogExportPage> match = pages.stream()
                    .filter(candidate -> candidate.page() == page)
                    .findFirst();
            return match.map(Mono::just).orElseGet(() -> Mono.error(
                    new IllegalStateException("unexpected page " + page)));
        }

        List<Integer> pages() {
            return new ArrayList<>(requestedPages);
        }

        List<CatalogEntity> entities() {
            return new ArrayList<>(entities);
        }

        List<Instant> updatedSince() {
            return new ArrayList<>(updatedSince);
        }

        List<String> tokens() {
            return new ArrayList<>(tokens);
        }
    }

    private static final class FakeProjection
            implements com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase {

        private final List<ProjectionResult> results;
        private int index;

        private FakeProjection(ProjectionResult... results) {
            this.results = List.of(results);
        }

        @Override
        public Mono<ProjectionResult> handle(
                com.smxplore.recommendations.domain.model.event.ProjectedEvent event) {
            ProjectionResult result = results.get(Math.min(index, results.size() - 1));
            index++;
            return Mono.just(result);
        }
    }
}
