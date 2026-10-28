package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.reservation.ReservationAvailability;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface CheckReservationAvailabilityUseCase {

    Mono<ReservationAvailability> handle(UUID serviceId, Instant bookedFor, int people);
}
