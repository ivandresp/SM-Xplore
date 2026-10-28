package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationFilter;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface ReservationRepositoryPort {

    Mono<Reservation> save(Reservation reservation);

    Mono<Reservation> findById(UUID reservationId);

    Mono<Reservation> findByIdempotencyKey(UUID touristId, String idempotencyKey);

    Mono<PageResult<Reservation>> findAll(ReservationFilter filter);

    Mono<Integer> reservedPeople(UUID serviceId, Instant from, Instant to);

    Mono<Void> lockService(UUID serviceId);

    Mono<Void> lockIdempotencyKey(UUID touristId, String idempotencyKey);

    Mono<Void> lockTourist(UUID touristId);

    Mono<Long> countActiveByTourist(UUID touristId, Instant now);

    Mono<Long> countCreatedSince(UUID touristId, Instant since);

    Flux<UUID> findCompletable(Instant cutoff, int limit);

    Mono<Boolean> updateStatus(Reservation reservation, ReservationStatus expectedStatus);
}
