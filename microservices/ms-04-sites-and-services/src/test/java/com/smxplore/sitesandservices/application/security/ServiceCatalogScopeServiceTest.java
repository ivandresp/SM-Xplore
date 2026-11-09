package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.service.ServiceCreateService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MS04-03: una prestadora solo puede vincular servicios a sitios publicados, de la zona
 * solicitada y administrados por el mismo gestor de esa zona.
 */
class ServiceCatalogScopeServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PROVIDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID OTHER_MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111102");
    private static final UUID SITE_ID = UUID.fromString("44444444-4444-4444-4444-444444444401");
    private static final UUID ZONE_ID = UUID.fromString("66666666-6666-6666-6666-666666666601");
    private static final UUID OTHER_ZONE_ID = UUID.fromString("66666666-6666-6666-6666-666666666602");
    private static final UUID CATEGORY_ID = UUID.fromString("77777777-7777-7777-7777-777777777701");

    private final CatalogAccess access = new CatalogAccess();
    private final Actor provider = new Actor(PROVIDER_ID, UserRole.SERVICE_PROVIDER, "Prestadora");

    private CatalogLookup lookup;
    private ServiceRepositoryPort serviceRepository;
    private ServiceCreateService createService;

    @BeforeEach
    void setUp() {
        lookup = mock(CatalogLookup.class);
        serviceRepository = mock(ServiceRepositoryPort.class);
        TransactionalOutboxWriter outboxWriter = mock(TransactionalOutboxWriter.class);

        when(serviceRepository.save(any(Service.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Mono.class));

        createService = new ServiceCreateService(
                serviceRepository, lookup, access, outboxWriter,
                mock(DomainEventFactory.class), CLOCK);
    }

    @Test
    void rejectsServicesLinkedToDraftSites() {
        when(lookup.serviceContext(SITE_ID, CATEGORY_ID, ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.ServiceContext(
                        site(ContentStatus.DRAFT, ZONE_ID, MANAGER_ID),
                        category(),
                        zone(ZONE_ID, MANAGER_ID))));

        StepVerifier.create(createService.handle(serviceRequest(SITE_ID, CATEGORY_ID, ZONE_ID), provider, "corr"))
                .expectErrorSatisfies(error -> {
                    org.assertj.core.api.Assertions.assertThat(error)
                            .isInstanceOf(InvalidCatalogException.class)
                            .hasMessageContaining("published sites");
                })
                .verify();
    }

    @Test
    void rejectsServicesWhoseZoneDoesNotBelongToTheSite() {
        when(lookup.serviceContext(SITE_ID, CATEGORY_ID, OTHER_ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.ServiceContext(
                        site(ContentStatus.PUBLISHED, ZONE_ID, MANAGER_ID),
                        category(),
                        zone(OTHER_ZONE_ID, MANAGER_ID))));

        StepVerifier.create(createService.handle(
                        serviceRequest(SITE_ID, CATEGORY_ID, OTHER_ZONE_ID), provider, "corr"))
                .expectErrorSatisfies(error -> {
                    org.assertj.core.api.Assertions.assertThat(error)
                            .isInstanceOf(InvalidCatalogException.class)
                            .hasMessageContaining("zone does not belong to the site");
                })
                .verify();
    }

    @Test
    void rejectsServicesWhoseSiteAndZoneHaveDifferentManagers() {
        when(lookup.serviceContext(SITE_ID, CATEGORY_ID, ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.ServiceContext(
                        site(ContentStatus.PUBLISHED, ZONE_ID, MANAGER_ID),
                        category(),
                        zone(ZONE_ID, OTHER_MANAGER_ID))));

        StepVerifier.create(createService.handle(serviceRequest(SITE_ID, CATEGORY_ID, ZONE_ID), provider, "corr"))
                .expectErrorSatisfies(error -> {
                    org.assertj.core.api.Assertions.assertThat(error)
                            .isInstanceOf(InvalidCatalogException.class)
                            .hasMessageContaining("same destination manager");
                })
                .verify();
    }

    @Test
    void createsServicesWhenTheSiteAndZoneAreCoherent() {
        when(lookup.serviceContext(SITE_ID, CATEGORY_ID, ZONE_ID)).thenReturn(Mono.just(
                new CatalogLookup.ServiceContext(
                        site(ContentStatus.PUBLISHED, ZONE_ID, MANAGER_ID),
                        category(),
                        zone(ZONE_ID, MANAGER_ID))));

        Service created = createService.handle(
                serviceRequest(SITE_ID, CATEGORY_ID, ZONE_ID), provider, "corr").block();

        org.assertj.core.api.Assertions.assertThat(created).isNotNull();
        verify(serviceRepository).save(any(Service.class));
    }

    private static Service serviceRequest(UUID siteId, UUID categoryId, UUID zoneId) {
        return Service.builder()
                .siteId(siteId)
                .categoryId(categoryId)
                .zoneId(zoneId)
                .name("Tour guiado")
                .description("Recorrido por la playa")
                .type("TOUR")
                .latitude(10.39)
                .longitude(-75.47)
                .price(new BigDecimal("50000"))
                .maxCapacity(20)
                .availability(List.of(new AvailabilitySlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), 20)))
                .photosUrls(List.of("https://example.com/service.jpg"))
                .cancellationPolicy("48 horas")
                .build();
    }

    private static Site site(ContentStatus status, UUID zoneId, UUID managerId) {
        return Site.builder()
                .siteId(SITE_ID)
                .managerId(managerId)
                .name("Playa de prueba")
                .description("Arena blanca")
                .categoryId(CATEGORY_ID)
                .categoryName("Naturaleza")
                .zoneId(zoneId)
                .zoneName("Centro")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(10.39)
                .longitude(-75.47)
                .photosUrls(List.of("https://example.com/site.jpg"))
                .status(status)
                .maxCapacity(100)
                .availability(List.of(new AvailabilitySlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 100)))
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
