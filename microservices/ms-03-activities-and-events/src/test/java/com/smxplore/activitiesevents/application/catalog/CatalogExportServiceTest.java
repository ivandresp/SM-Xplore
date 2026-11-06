package com.smxplore.activitiesevents.application.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.domain.exceptions.catalog.CatalogExportNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.catalog.InvalidCatalogExportException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.catalog.CatalogEntity;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * El feed debe devolver el mismo payload que los eventos vivos y la misma forma de página que ms-04,
 * porque ms-05 consume los dos servicios con un único DTO.
 */
@ExtendWith(MockitoExtension.class)
class CatalogExportServiceTest {

    private static final String ADMIN = "ADMIN";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private ActivityRepositoryPort activities;
    @Mock
    private EventRepositoryPort events;

    @Test
    void exportsActivitiesWithTheLiveEventPayload() {
        var updatedAt = Instant.parse("2026-11-04T22:00:00Z");
        var activity = Activity.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .guideId(UUID.randomUUID())
                .name("Snorkel")
                .category("AVENTURA")
                .price(new java.math.BigDecimal("45000"))
                .maxCapacity(10)
                .durationMinutes(90)
                .startsAt(Instant.parse("2026-11-05T14:00:00Z"))
                .endsAt(Instant.parse("2026-11-05T16:00:00Z"))
                .updatedAt(updatedAt)
                .build();
        given(activities.findUpdatedSince(eq(updatedAt), eq(0), eq(50)))
                .willReturn(Mono.just(new PageResult<>(List.of(activity), 0, 50, 1)));

        StepVerifier.create(service().handle(CatalogEntity.ACTIVITIES, updatedAt, 0, 50, ADMIN))
                .assertNext(page -> {
                    assertThat(page.content()).hasSize(1);
                    var item = page.content().getFirst();
                    assertThat(item.eventType()).isEqualTo("activities.activity.changed");
                    assertThat(item.eventVersion()).isEqualTo(2);
                    assertThat(item.aggregateId()).isEqualTo(activity.getId());
                    assertThat(item.updatedAt()).isEqualTo(updatedAt);
                    assertThat(item.payload()).containsEntry("activityId", activity.getId());
                    assertThat(item.payload()).containsEntry("updatedAt", updatedAt.toString());
                    assertThat(page.page()).isZero();
                    assertThat(page.size()).isEqualTo(50);
                    assertThat(page.total()).isEqualTo(1);
                    assertThat(page.hasNext()).isFalse();
                    assertThat(page.updatedSince()).isEqualTo(updatedAt);
                })
                .verifyComplete();
    }

    @Test
    void exportsEventsAndReportsHasNextWhenThePageIsNotTheLastOne() {
        var updatedAt = Instant.parse("2026-11-04T22:00:00Z");
        var event = Event.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .name("Fiesta")
                .category("CULTURA")
                .price(new java.math.BigDecimal("15000"))
                .maxCapacity(100)
                .updatedAt(updatedAt)
                .build();
        given(events.findUpdatedSince(eq(null), eq(0), eq(1)))
                .willReturn(Mono.just(new PageResult<>(List.of(event), 0, 1, 3)));

        StepVerifier.create(service().handle(CatalogEntity.EVENTS, null, 0, 1, ADMIN))
                .assertNext(page -> {
                    assertThat(page.content()).hasSize(1);
                    assertThat(page.content().getFirst().eventType()).isEqualTo("activities.event.changed");
                    assertThat(page.content().getFirst().eventVersion()).isEqualTo(2);
                    assertThat(page.content().getFirst().payload()).containsEntry("eventId", event.getId());
                    assertThat(page.hasNext()).isTrue();
                    assertThat(page.updatedSince()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void rejectsNonAdmins() {
        StepVerifier.create(service().handle(CatalogEntity.ACTIVITIES, null, 0, 50, "GUIDE"))
                .expectError(CatalogExportNotAllowedException.class)
                .verify();
    }

    @Test
    void rejectsAnOversizedPage() {
        StepVerifier.create(service().handle(CatalogEntity.EVENTS, null, 0, 201, ADMIN))
                .expectError(InvalidCatalogExportException.class)
                .verify();
    }

    @Test
    void rejectsNegativePages() {
        StepVerifier.create(service().handle(CatalogEntity.EVENTS, null, -1, 50, ADMIN))
                .expectError(InvalidCatalogExportException.class)
                .verify();
    }

    private CatalogExportService service() {
        return new CatalogExportService(activities, events,
                new ActivityChangedEventFactory(objectMapper), new EventChangedEventFactory(objectMapper));
    }
}
