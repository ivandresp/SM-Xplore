package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.application.support.ReservationAvailabilityPolicy;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationAvailability;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.ports.in.CheckReservationAvailabilityUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReservationAvailabilityService implements CheckReservationAvailabilityUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final ReservationAvailabilityPolicy availabilityPolicy;
    private final Clock clock;

    @Override
    public Mono<ReservationAvailability> handle(UUID serviceId, Instant bookedFor, int people) {
        if (serviceId == null) {
            return Mono.error(new InvalidCatalogException("serviceId is required"));
        }
        if (bookedFor == null) {
            return Mono.error(new InvalidCatalogException("bookedFor is required"));
        }
        if (people < 1) {
            return Mono.error(new InvalidCatalogException("people must be >= 1"));
        }

        return serviceRepository.findById(serviceId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Service", serviceId)))
                .flatMap(service -> availability(service, bookedFor, people));
    }

    private Mono<ReservationAvailability> availability(
            Service service,
            Instant bookedFor,
            int people
    ) {
        if (!bookedFor.isAfter(Instant.now(clock))) {
            return Mono.just(result(
                    service,
                    bookedFor,
                    people,
                    0,
                    false,
                    ReservationAvailabilityPolicy.BOOKED_FOR_IN_PAST
            ));
        }
        String problem = availabilityPolicy.availabilityProblem(service, bookedFor);
        if (problem != null) {
            return Mono.just(result(service, bookedFor, people, 0, false, problem));
        }
        Instant from = availabilityPolicy.dayStart(bookedFor);
        Instant to = availabilityPolicy.dayEnd(bookedFor);
        return reservationRepository.reservedPeople(service.serviceId(), from, to)
                .map(reserved -> {
                    int remaining = Math.max(0, service.maxCapacity() - reserved);
                    boolean available = people <= remaining;
                    String reason = available
                            ? null
                            : ReservationAvailabilityPolicy.INSUFFICIENT_CAPACITY;
                    return result(service, bookedFor, people, reserved, available, reason);
                });
    }

    private ReservationAvailability result(
            Service service,
            Instant bookedFor,
            int people,
            int reservedPeople,
            boolean available,
            String reason
    ) {
        return new ReservationAvailability(
                service.serviceId(),
                service.name(),
                service.providerId(),
                bookedFor,
                availabilityPolicy.serviceDate(bookedFor),
                service.maxCapacity(),
                reservedPeople,
                people,
                available,
                reason
        );
    }
}
