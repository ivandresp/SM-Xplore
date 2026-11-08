package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.site.SiteAssignZoneService;
import com.smxplore.sitesandservices.application.site.SiteCreateService;
import com.smxplore.sitesandservices.application.site.SiteUpdateService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS04-04: una gestora solo puede crear o mover sitios dentro de las zonas que ella administra;
 * el ADMIN puede hacerlo en cualquier zona.
 */
class SiteZoneOwnershipServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID OTHER_MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111102");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333301");
    private static final UUID SITE_ID = UUID.fromString("44444444-4444-4444-4444-444444444401");
    private static final UUID ZONE_ID = UUID.fromString("66666666-6666-6666-6666-666666666601");
    private static final UUID FOREIGN_ZONE_ID = UUID.fromString("66666666-6666-6666-6666-666666666602");
    private static final UUID CATEGORY_ID = UUID.fromString("77777777-7777-7777-7777-777777777701");

    private final CatalogAccess access = new CatalogAccess();
    private final Actor manager = new Actor(MANAGER_ID, UserRole.DESTINATION_MANAGER, "Gestora");
    private final Actor admin = new Actor(ADMIN_ID, UserRole.ADMIN, "Admin");

    private CatalogLookup lookup;
    private SiteRepositoryPort siteRepository;
    private SiteCreateService createService;
    private SiteUpdateService updateService;
    private SiteAssignZoneService assignZoneService;

    @BeforeEach
    void setUp() {
        lookup = mock(CatalogLookup.class);
        siteRepository = mock(SiteRepositoryPort.class);
        ServiceRepositoryPort serviceRepository = mock(ServiceRepositoryPort.class);
        TransactionalOutboxWriter outboxWriter = mock(TransactionalOutboxWriter.class);
        DomainEventFactory eventFactory = mock(DomainEventFactory.class);

        when(siteRepository.save(any(Site.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Mono.class));
        when(serviceRepository.updateSiteSnapshot(any(), any())).thenReturn(Mono.empty());

        createService = new SiteCreateService(
                siteRepository, lookup, access, outboxWriter, eventFactory, CLOCK);
        updateService = new SiteUpdateService(
                siteRepository, serviceRepository, lookup, access, outboxWriter, eventFactory, CLOCK);
        assignZoneService = new SiteAssignZoneService(
                siteRepository, lookup, access, outboxWriter, eventFactory, CLOCK);
    }

    @Test
    void managerCannotCreateSitesInAnotherManagersZone() {
        when(lookup.siteContext(CATEGORY_ID, FOREIGN_ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.SiteContext(category(), zone(FOREIGN_ZONE_ID, OTHER_MANAGER_ID))));

        StepVerifier.create(createService.handle(
                        siteRequest(CATEGORY_ID, FOREIGN_ZONE_ID), manager, "corr"))
                .expectError(ForbiddenOperationException.class)
                .verify();

        verifyNoInteractions(siteRepository);
    }

    @Test
    void managerCanCreateSitesInItsOwnZone() {
        when(lookup.siteContext(CATEGORY_ID, ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.SiteContext(category(), zone(ZONE_ID, MANAGER_ID))));

        Site created = createService.handle(siteRequest(CATEGORY_ID, ZONE_ID), manager, "corr").block();

        assertThat(created).isNotNull();
        assertThat(created.managerId()).isEqualTo(MANAGER_ID);
    }

    @Test
    void adminCanCreateSitesInAnyZone() {
        when(lookup.siteContext(CATEGORY_ID, FOREIGN_ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.SiteContext(category(), zone(FOREIGN_ZONE_ID, OTHER_MANAGER_ID))));

        Site created = createService.handle(siteRequest(CATEGORY_ID, FOREIGN_ZONE_ID), admin, "corr").block();

        assertThat(created).isNotNull();
    }

    @Test
    void managerCannotMoveItsSiteToAnotherManagersZone() {
        Site existing = existingSite(ZONE_ID);
        when(lookup.site(SITE_ID)).thenReturn(Mono.just(existing));
        when(lookup.zone(FOREIGN_ZONE_ID)).thenReturn(Mono.just(zone(FOREIGN_ZONE_ID, OTHER_MANAGER_ID)));

        StepVerifier.create(assignZoneService.handle(
                        Site.builder().siteId(SITE_ID).zoneId(FOREIGN_ZONE_ID).build(), manager, "corr"))
                .expectError(ForbiddenOperationException.class)
                .verify();
    }

    @Test
    void adminCanMoveSitesToAnotherManagersZone() {
        Site existing = existingSite(ZONE_ID);
        when(lookup.site(SITE_ID)).thenReturn(Mono.just(existing));
        when(lookup.zone(FOREIGN_ZONE_ID)).thenReturn(Mono.just(zone(FOREIGN_ZONE_ID, OTHER_MANAGER_ID)));

        Site updated = assignZoneService.handle(
                Site.builder().siteId(SITE_ID).zoneId(FOREIGN_ZONE_ID).build(), admin, "corr").block();

        assertThat(updated).isNotNull();
        assertThat(updated.zoneId()).isEqualTo(FOREIGN_ZONE_ID);
    }

    @Test
    void managerCannotUpdateItsSiteIntoAnotherManagersZone() {
        Site existing = existingSite(ZONE_ID);
        when(lookup.site(SITE_ID)).thenReturn(Mono.just(existing));
        when(lookup.siteContext(CATEGORY_ID, FOREIGN_ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.SiteContext(category(), zone(FOREIGN_ZONE_ID, OTHER_MANAGER_ID))));

        StepVerifier.create(updateService.handle(
                        siteRequest(CATEGORY_ID, FOREIGN_ZONE_ID).toBuilder().siteId(SITE_ID).build(),
                        manager, "corr"))
                .expectError(ForbiddenOperationException.class)
                .verify();
    }

    private static Site siteRequest(UUID categoryId, UUID zoneId) {
        return Site.builder()
                .name("Playa de prueba")
                .description("Arena blanca")
                .categoryId(categoryId)
                .zoneId(zoneId)
                .latitude(10.39)
                .longitude(-75.47)
                .photosUrls(List.of("https://example.com/site.jpg"))
                .maxCapacity(100)
                .availability(List.of(new AvailabilitySlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 100)))
                .build();
    }

    private static Site existingSite(UUID zoneId) {
        return siteRequest(CATEGORY_ID, zoneId).toBuilder()
                .siteId(SITE_ID)
                .managerId(MANAGER_ID)
                .categoryName("Naturaleza")
                .zoneName("Centro")
                .zoneStatus(ZoneStatus.ACTIVE)
                .status(ContentStatus.PUBLISHED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static Zone zone(UUID zoneId, UUID managerId) {
        return Zone.builder()
                .zoneId(zoneId)
                .managerId(managerId)
                .name("Centro")
                .status(ZoneStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static Category category() {
        return Category.builder()
                .categoryId(CATEGORY_ID)
                .name("Naturaleza")
                .scope(CategoryScope.GENERAL)
                .updatedAt(NOW)
                .build();
    }
}
