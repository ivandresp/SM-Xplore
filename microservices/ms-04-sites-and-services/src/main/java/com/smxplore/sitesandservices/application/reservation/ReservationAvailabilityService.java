package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.application.support.ReservationAvailabilityPolicy;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
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
                    ReservationAvailabilityPolicy.BOOKED_FOR_IN_PAST,
                    service.maxCapacity()
            ));
        }
        String problem = availabilityPolicy.availabilityProblem(service, bookedFor);
        if (problem != null) {
            return Mono.just(result(service, bookedFor, people, 0, false, problem, service.maxCapacity()));
        }
        AvailabilitySlot slot = availabilityPolicy.slotFor(service, bookedFor);
        Integer slotCapacity = slot == null ? null : slot.capacity();
        Mono<Integer> reservedToday = reservationRepository.reservedPeople(
                service.serviceId(),
                availabilityPolicy.dayStart(bookedFor),
                availabilityPolicy.dayEnd(bookedFor));
        Mono<Integer> reservedInSlot = slotCapacity == null
                ? Mono.just(0)
                : reservationRepository.reservedPeople(
                        service.serviceId(),
                        availabilityPolicy.slotStart(bookedFor, slot),
                        availabilityPolicy.slotEnd(bookedFor, slot));
        return Mono.zip(reservedToday, reservedInSlot)
                .map(tuple -> {
                    int dailyRemaining = Math.max(0, service.maxCapacity() - tuple.getT1());
                    int slotRemaining = slotCapacity == null
                            ? Integer.MAX_VALUE
                            : Math.max(0, slotCapacity - tuple.getT2());
                    boolean slotIsTheLimit = slotRemaining < dailyRemaining;
                    int capacity = slotIsTheLimit ? slotCapacity : service.maxCapacity();
                    int reserved = slotIsTheLimit ? tuple.getT2() : tuple.getT1();
                    boolean available = people <= Math.min(dailyRemaining, slotRemaining);
                    String reason = available
                            ? null
                            : ReservationAvailabilityPolicy.INSUFFICIENT_CAPACITY;
                    return result(service, bookedFor, people, reserved, available, reason, capacity);
                });
    }

    private ReservationAvailability result(
            Service service,
            Instant bookedFor,
            int people,
            int reservedPeople,
            boolean available,
            String reason,
            int capacity
    ) {
        return new ReservationAvailability(
                service.serviceId(),
                service.name(),
                service.providerId(),
                bookedFor,
                availabilityPolicy.serviceDate(bookedFor),
                capacity,
                reservedPeople,
                people,
                available,
                reason
        );
    }
}
