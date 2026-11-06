package com.smxplore.activitiesevents.application.indicator;

import com.smxplore.activitiesevents.domain.exceptions.indicator.DemandAccessDeniedException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.indicator.DemandComparison;
import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandGranularity;
import com.smxplore.activitiesevents.domain.model.indicator.DemandRecord;
import com.smxplore.activitiesevents.domain.model.indicator.DemandSummary;
import com.smxplore.activitiesevents.domain.model.indicator.ServiceDemand;
import com.smxplore.activitiesevents.domain.model.indicator.SiteDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ZoneDemand;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.DemandQueryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.Ms04DemandPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DemandIndicatorServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private DemandQueryPort demandQuery;
    @Mock
    private ActivityRepositoryPort activityRepository;
    @Mock
    private EventRepositoryPort eventRepository;
    @Mock
    private SiteCatalogPort siteCatalog;
    @Mock
    private Ms04DemandPort ms04Demand;

    private DemandIndicatorService service;

    @BeforeEach
    void setUp() {
        service = new DemandIndicatorService(demandQuery, activityRepository, eventRepository,
                siteCatalog, ms04Demand, BOGOTA, CLOCK);
    }

    @Test
    void guideOnlySeesTheirOwnActivitiesAndDoesNotCallMs04() {
        var guideId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var activity = activity(guideId, siteId, "Snorkel", 10);
        var emptyActivity = activity(guideId, UUID.randomUUID(), "Caminata", 8);
        given(activityRepository.findAllByGuideId(guideId))
                .willReturn(Flux.just(activity, emptyActivity));
        given(siteCatalog.findById(siteId, "token"))
                .willReturn(Mono.just(site(siteId, "Playa", "Zona Norte")));
        given(siteCatalog.findById(emptyActivity.getSiteId(), "token"))
                .willReturn(Mono.just(site(emptyActivity.getSiteId(), "Sierra", "Zona Sierra")));
        given(demandQuery.find(any())).willReturn(Flux.just(
                record(activity, 2, ReservationStatus.CONFIRMED)));

        var filter = new DemandFilter(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
                null, null, DemandGranularity.DAY);

        StepVerifier.create(service.handle(filter, guideId, UserRole.GUIDE, "token"))
                .assertNext(result -> {
                    assertThat(result.scope()).isEqualTo("GUIDE");
                    assertThat(result.summary().reservations()).isEqualTo(1);
                    assertThat(result.summary().people()).isEqualTo(2);
                    assertThat(result.activities()).hasSize(2);
                    assertThat(result.activities().getFirst().name()).isEqualTo("Snorkel");
                    assertThat(result.events()).isEmpty();
                    assertThat(result.services()).isEmpty();
                    assertThat(result.partial()).isFalse();
                    assertThat(result.zones()).extracting(zone -> zone.zoneName())
                            .containsExactlyInAnyOrder("Zona Norte", "Zona Sierra");
                    assertThat(result.activities().getFirst().occupancyRate()).isEqualTo(0.2);
                })
                .verifyComplete();

        then(ms04Demand).shouldHaveNoInteractions();
    }

    @Test
    void managerGetsActivitiesEventsAndServicesInOneView() {
        var managerId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var zoneId = UUID.randomUUID();
        var activity = activity(UUID.randomUUID(), siteId, "Snorkel", 10);
        var event = event(managerId, siteId, "Concierto", 20, 10);
        var site = new SiteCatalogPort.SiteSnapshot(siteId, managerId, "Playa", zoneId,
                "Zona Norte", "AVAILABLE");
        given(siteCatalog.findByManagerId(managerId, "token")).willReturn(Flux.just(site));
        given(activityRepository.findIdsBySiteIds(List.of(siteId)))
                .willReturn(Flux.just(activity.getId()));
        given(eventRepository.findIdsBySiteIds(List.of(siteId)))
                .willReturn(Flux.just(event.getId()));
        given(activityRepository.findByIds(List.of(activity.getId())))
                .willReturn(Flux.just(activity));
        given(eventRepository.findByIds(List.of(event.getId())))
                .willReturn(Flux.just(event));
        given(demandQuery.find(any())).willReturn(Flux.just(
                record(activity, 2, ReservationStatus.CONFIRMED),
                record(event, 3, ReservationStatus.CONFIRMED)));
        given(ms04Demand.fetch(any(), any())).willReturn(Mono.just(services(zoneId, siteId)));

        var filter = new DemandFilter(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
                null, null, DemandGranularity.DAY);

        StepVerifier.create(service.handle(filter, managerId, UserRole.DESTINATION_MANAGER, "token"))
                .assertNext(result -> {
                    assertThat(result.scope()).isEqualTo("MANAGER");
                    assertThat(result.summary().reservations()).isEqualTo(3);
                    assertThat(result.summary().people()).isEqualTo(9);
                    assertThat(result.activities()).hasSize(1);
                    assertThat(result.events()).hasSize(1);
                    assertThat(result.events().getFirst().expectedAttendance()).isEqualTo(10);
                    assertThat(result.events().getFirst().attendanceRate()).isEqualTo(0.3);
                    assertThat(result.services()).hasSize(1);
                    assertThat(result.services().getFirst().serviceName()).isEqualTo("Tour");
                    assertThat(result.zones()).hasSize(1);
                    assertThat(result.zones().getFirst().people()).isEqualTo(9);
                    assertThat(result.partial()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void managerCannotAskForAnotherManagersZone() {
        var managerId = UUID.randomUUID();
        var site = site(UUID.randomUUID(), "Playa", "Zona Norte");
        given(siteCatalog.findByManagerId(managerId, "token")).willReturn(Flux.just(site));
        var filter = new DemandFilter(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
                UUID.randomUUID(), null, DemandGranularity.DAY);

        StepVerifier.create(service.handle(filter, managerId, UserRole.DESTINATION_MANAGER, "token"))
                .expectError(DemandAccessDeniedException.class)
                .verify();
    }

    @Test
    void marksTheViewPartialWhenMs04IsDown() {
        var managerId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var site = site(siteId, "Playa", "Zona Norte");
        given(siteCatalog.findByManagerId(managerId, "token")).willReturn(Flux.just(site));
        given(activityRepository.findIdsBySiteIds(List.of(siteId))).willReturn(Flux.empty());
        given(eventRepository.findIdsBySiteIds(List.of(siteId))).willReturn(Flux.empty());
        given(activityRepository.findByIds(any())).willReturn(Flux.empty());
        given(eventRepository.findByIds(any())).willReturn(Flux.empty());
        given(ms04Demand.fetch(any(), any()))
                .willReturn(Mono.error(new IllegalStateException("ms-04 down")));

        var filter = new DemandFilter(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
                null, null, DemandGranularity.DAY);

        StepVerifier.create(service.handle(filter, managerId, UserRole.DESTINATION_MANAGER, "token"))
                .assertNext(result -> {
                    assertThat(result.partial()).isTrue();
                    assertThat(result.services()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void rejectsTourists() {
        var filter = new DemandFilter(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
                null, null, DemandGranularity.DAY);

        StepVerifier.create(service.handle(filter, UUID.randomUUID(), UserRole.TOURIST, "token"))
                .expectError(DemandAccessDeniedException.class)
                .verify();
    }

    private static Activity activity(UUID guideId, UUID siteId, String name, int capacity) {
        var start = NOW.plusSeconds(3_600);
        return Activity.builder()
                .siteId(siteId)
                .guideId(guideId)
                .name(name)
                .category("Aventura")
                .status(ActivityStatus.PUBLISHED)
                .price(new BigDecimal("10000.00"))
                .maxCapacity(capacity)
                .durationMinutes(60)
                .startsAt(start)
                .endsAt(start.plusSeconds(3_600))
                .build();
    }

    private static Event event(UUID managerId, UUID siteId, String name, int capacity, int expected) {
        var start = NOW.plusSeconds(3_600);
        return Event.builder()
                .siteId(siteId)
                .siteName("Playa")
                .managerId(managerId)
                .name(name)
                .category("Cultura")
                .status(EventStatus.PUBLISHED)
                .price(new BigDecimal("15000.00"))
                .maxCapacity(capacity)
                .expectedAttendance(expected)
                .startsAt(start)
                .endsAt(start.plusSeconds(3_600))
                .build();
    }

    private static SiteCatalogPort.SiteSnapshot site(UUID siteId, String name, String zoneName) {
        return new SiteCatalogPort.SiteSnapshot(siteId, UUID.randomUUID(), name, UUID.randomUUID(),
                zoneName, "AVAILABLE");
    }

    private static DemandRecord record(Activity activity, int people, ReservationStatus status) {
        return new DemandRecord(UUID.randomUUID(), UUID.randomUUID(), status,
                activity.getStartsAt(), ReservationItemType.ACTIVITY, activity.getId(),
                activity.getName(), activity.getSiteId(), null, activity.getGuideId(), null, 0,
                activity.getMaxCapacity(), activity.getCategory(), people,
                activity.getPrice().multiply(BigDecimal.valueOf(people)));
    }

    private static DemandRecord record(Event event, int people, ReservationStatus status) {
        return new DemandRecord(UUID.randomUUID(), UUID.randomUUID(), status,
                event.getStartsAt(), ReservationItemType.EVENT, event.getId(),
                event.getName(), event.getSiteId(), event.getSiteName(), null, event.getManagerId(),
                event.getExpectedAttendance(), event.getMaxCapacity(), event.getCategory(), people,
                event.getPrice().multiply(BigDecimal.valueOf(people)));
    }

    private static Ms04DemandPort.ServicesDemand services(UUID zoneId, UUID siteId) {
        var empty = DemandSummary.empty();
        var summary = new DemandSummary(1, 4, new BigDecimal("40000.00"), 0, 0, 1, 0,
                4);
        var previous = new DemandSummary(1, 2, new BigDecimal("20000.00"), 0, 0, 1, 0,
                2);
        return new Ms04DemandPort.ServicesDemand(
                summary,
                new DemandComparison(summary, previous, 0, 100, 100, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ZoneDemand(zoneId, "Zona Norte", "AVAILABLE", 1, 4,
                        new BigDecimal("40000.00"), 1, 20, 0.2)),
                List.of(new SiteDemand(siteId, "Playa", zoneId, "Zona Norte", 1, 4,
                        new BigDecimal("40000.00"), 1, 20, 0.2)),
                List.of(new ServiceDemand(UUID.randomUUID(), "Tour", "TOUR", siteId, "Playa",
                        zoneId, "Zona Norte", 1, 4, new BigDecimal("40000.00"), 1, 20, 0.2)));
    }
}
