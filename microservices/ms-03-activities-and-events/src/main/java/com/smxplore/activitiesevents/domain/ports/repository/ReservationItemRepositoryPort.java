package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReservationItemRepositoryPort {
    Mono<ReservationItem> save(ReservationItem reservationItem);
    Flux<ReservationItem> saveAll(Iterable<ReservationItem> reservationItems);
    Mono<ReservationItem> findById(UUID id);
    Flux<ReservationItem> findByReservationId(UUID reservationId);
    Flux<ReservationItem> findByItemId(UUID itemId);
    Flux<ReservationItem> findByItemType(ReservationItemType reservationItemType, int limit);
}
