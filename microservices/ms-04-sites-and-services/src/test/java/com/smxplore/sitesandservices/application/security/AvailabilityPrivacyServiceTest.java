package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.reservation.ReservationAvailabilityService;
import com.smxplore.sitesandservices.application.support.InMemoryRateLimiter;
import com.smxplore.sitesandservices.application.support.ReservationAvailabilityPolicy;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.RateLimitExceededException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationAvailability;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS04-08: la disponibilidad exige autenticacion, oculta el cupo a los turistas y limita las
 * peticiones por cuenta.
 */
class AvailabilityPrivacyServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();

    private final ServiceRepositoryPort serviceRepository = mock(ServiceRepositoryPort.class);
    private final ReservationRepositoryPort reservationRepository = mock(ReservationRepositoryPort.class);

    @Test
    void anonymousCallersAreRejected() {
        ReservationAvailabilityService service = service(new InMemoryRateLimiter(10, Duration.ofMinutes(1)));

        StepVerifier.create(service.handle(SERVICE_ID, CLOCK.instant().plusSeconds(3600), 2, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class))
                .verify();

        verifyNoInteractions(serviceRepository, reservationRepository);
    }

    @Test
    void touristsOnlySeeBooleanAvailability() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Mono.just(publishedService()));
        ReservationAvailabilityService service = service(new InMemoryRateLimiter(10, Duration.ofMinutes(1)));
        Actor tourist = new Actor(UUID.randomUUID(), UserRole.TOURIST, "Turista");

        ReservationAvailability availability = service.handle(
                        SERVICE_ID, CLOCK.instant().minusSeconds(3600), 2, tourist)
                .block();

        assertThat(availability).isNotNull();
        assertThat(availability.providerId()).isNull();
        assertThat(availability.maxCapacity()).isNull();
        assertThat(availability.reservedPeople()).isNull();
        assertThat(availability.available()).isFalse();
    }

    @Test
    void otherProvidersCannotInspectTheCapacity() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Mono.just(publishedService()));
        ReservationAvailabilityService service = service(new InMemoryRateLimiter(10, Duration.ofMinutes(1)));
        Actor otherProvider = new Actor(UUID.randomUUID(), UserRole.SERVICE_PROVIDER, "Otra");

        StepVerifier.create(service.handle(
                        SERVICE_ID, CLOCK.instant().plusSeconds(3600), 2, otherProvider))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("Only tourists"))
                .verify();
    }

    @Test
    void repeatedRequestsAreRateLimited() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Mono.just(publishedService()));
        ReservationAvailabilityService service = service(new InMemoryRateLimiter(1, Duration.ofMinutes(1)));
        Actor tourist = new Actor(UUID.randomUUID(), UserRole.TOURIST, "Turista");
        Instant bookedFor = CLOCK.instant().minusSeconds(3600);

        StepVerifier.create(service.handle(SERVICE_ID, bookedFor, 2, tourist))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(service.handle(SERVICE_ID, bookedFor, 2, tourist))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(RateLimitExceededException.class))
                .verify();
    }

    private ReservationAvailabilityService service(InMemoryRateLimiter limiter) {
        return new ReservationAvailabilityService(
                serviceRepository,
                reservationRepository,
                new ReservationAvailabilityPolicy(ZoneId.of("America/Bogota")),
                limiter,
                CLOCK);
    }

    private static Service publishedService() {
        return Service.builder()
                .serviceId(SERVICE_ID)
                .providerId(PROVIDER_ID)
                .providerName("Prestadora")
                .name("Tour")
                .maxCapacity(10)
                .status(ContentStatus.PUBLISHED)
                .zoneStatus(ZoneStatus.ACTIVE)
                .build();
    }
}
