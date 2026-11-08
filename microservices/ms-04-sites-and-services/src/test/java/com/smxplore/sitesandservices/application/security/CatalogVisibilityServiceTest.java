package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.service.ServiceGetService;
import com.smxplore.sitesandservices.application.service.ServiceListService;
import com.smxplore.sitesandservices.application.site.SiteGetService;
import com.smxplore.sitesandservices.application.site.SiteListService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MS04-02: los listados y el detalle publicos solo exponen contenido PUBLISHED; el personal
 * autorizado (ADMIN o el dueño) conserva acceso a borradores e inactivos.
 */
class CatalogVisibilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID OTHER_MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111102");
    private static final UUID PROVIDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333301");
    private static final UUID SITE_ID = UUID.fromString("44444444-4444-4444-4444-444444444401");
    private static final UUID SERVICE_ID = UUID.fromString("55555555-5555-5555-5555-555555555501");
    private static final UUID ZONE_ID = UUID.fromString("66666666-6666-6666-6666-666666666601");
    private static final UUID CATEGORY_ID = UUID.fromString("77777777-7777-7777-7777-777777777701");

    private final CatalogAccess access = new CatalogAccess();
    private final Actor manager = new Actor(MANAGER_ID, UserRole.DESTINATION_MANAGER, "Gestora");
    private final Actor provider = new Actor(PROVIDER_ID, UserRole.SERVICE_PROVIDER, "Prestadora");
    private final Actor admin = new Actor(ADMIN_ID, UserRole.ADMIN, "Admin");

    private CatalogLookup lookup;
    private SiteRepositoryPort siteRepository;
    private ServiceRepositoryPort serviceRepository;
    private SiteGetService siteGetService;
    private ServiceGetService serviceGetService;
    private SiteListService siteListService;
    private ServiceListService serviceListService;

    @BeforeEach
    void setUp() {
        lookup = mock(CatalogLookup.class);
        siteRepository = mock(SiteRepositoryPort.class);
        serviceRepository = mock(ServiceRepositoryPort.class);
        siteGetService = new SiteGetService(lookup, access);
        serviceGetService = new ServiceGetService(lookup, access);
        siteListService = new SiteListService(siteRepository, access);
        serviceListService = new ServiceListService(serviceRepository, access);
    }

    @Test
    void anonymousCallersCannotReadDraftSites() {
        when(lookup.site(SITE_ID)).thenReturn(Mono.just(site(ContentStatus.DRAFT)));

        StepVerifier.create(siteGetService.handle(SITE_ID, null))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void owningManagerAndAdminCanReadDraftSites() {
        when(lookup.site(SITE_ID)).thenReturn(Mono.just(site(ContentStatus.DRAFT)));

        assertThat(siteGetService.handle(SITE_ID, manager).block()).isNotNull();
        assertThat(siteGetService.handle(SITE_ID, admin).block()).isNotNull();
    }

    @Test
    void anonymousCallersCannotReadDraftServices() {
        when(lookup.service(SERVICE_ID)).thenReturn(Mono.just(service(ContentStatus.DRAFT)));

        StepVerifier.create(serviceGetService.handle(SERVICE_ID, null))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void owningProviderAndAdminCanReadDraftServices() {
        when(lookup.service(SERVICE_ID)).thenReturn(Mono.just(service(ContentStatus.DRAFT)));

        assertThat(serviceGetService.handle(SERVICE_ID, provider).block()).isNotNull();
        assertThat(serviceGetService.handle(SERVICE_ID, admin).block()).isNotNull();
    }

    @Test
    void anonymousServiceListIsForcedToPublishedStatus() {
        when(serviceRepository.findAll(any(ServiceFilter.class)))
                .thenReturn(Mono.just(new PageResult<>(List.of(), 0, 20, 0)));
        ServiceFilter request = new ServiceFilter(
                null, null, null, null, ContentStatus.DRAFT, null, null, null, null, 0, 20);

        serviceListService.handle(request, null).block();

        ArgumentCaptor<ServiceFilter> captor = ArgumentCaptor.forClass(ServiceFilter.class);
        verify(serviceRepository).findAll(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void owningProviderKeepsItsDraftServiceFilter() {
        when(serviceRepository.findAll(any(ServiceFilter.class)))
                .thenReturn(Mono.just(new PageResult<>(List.of(), 0, 20, 0)));
        ServiceFilter request = new ServiceFilter(
                null, null, null, PROVIDER_ID, ContentStatus.DRAFT, null, null, null, null, 0, 20);

        serviceListService.handle(request, provider).block();

        ArgumentCaptor<ServiceFilter> captor = ArgumentCaptor.forClass(ServiceFilter.class);
        verify(serviceRepository).findAll(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    void anonymousSiteListIsForcedToPublishedStatus() {
        when(siteRepository.findAll(any(SiteFilter.class)))
                .thenReturn(Mono.just(new PageResult<>(List.of(), 0, 20, 0)));
        SiteFilter request = new SiteFilter(
                null, null, ContentStatus.INACTIVE, null, null, 0, 20);

        siteListService.handle(request, null).block();

        ArgumentCaptor<SiteFilter> captor = ArgumentCaptor.forClass(SiteFilter.class);
        verify(siteRepository).findAll(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void anonymousSiteListCannotFilterByManager() {
        SiteFilter request = new SiteFilter(null, null, null, null, OTHER_MANAGER_ID, 0, 20);

        StepVerifier.create(siteListService.handle(request, null))
                .expectError(ForbiddenOperationException.class)
                .verify();
    }

    @Test
    void owningManagerKeepsItsDraftSiteFilter() {
        when(siteRepository.findAll(any(SiteFilter.class)))
                .thenReturn(Mono.just(new PageResult<>(List.of(), 0, 20, 0)));
        SiteFilter request = new SiteFilter(null, null, ContentStatus.DRAFT, null, MANAGER_ID, 0, 20);

        siteListService.handle(request, manager).block();

        ArgumentCaptor<SiteFilter> captor = ArgumentCaptor.forClass(SiteFilter.class);
        verify(siteRepository).findAll(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ContentStatus.DRAFT);
    }

    private static Site site(ContentStatus status) {
        return Site.builder()
                .siteId(SITE_ID)
                .managerId(MANAGER_ID)
                .name("Playa de prueba")
                .description("Arena blanca")
                .categoryId(CATEGORY_ID)
                .categoryName("Naturaleza")
                .zoneId(ZONE_ID)
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

    private static Service service(ContentStatus status) {
        return Service.builder()
                .serviceId(SERVICE_ID)
                .providerId(PROVIDER_ID)
                .providerName("Prestadora")
                .siteId(SITE_ID)
                .siteName("Playa de prueba")
                .zoneId(ZONE_ID)
                .zoneName("Centro")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(CATEGORY_ID)
                .categoryName("Naturaleza")
                .name("Tour guiado")
                .description("Recorrido por la playa")
                .type("TOUR")
                .latitude(10.39)
                .longitude(-75.47)
                .price(new BigDecimal("50000.00"))
                .status(status)
                .maxCapacity(20)
                .availability(List.of(new AvailabilitySlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), 20)))
                .photosUrls(List.of("https://example.com/service.jpg"))
                .cancellationPolicy("48 horas")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
