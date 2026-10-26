package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Minimal in-memory reservation repository for use case tests that do not touch reservations. */
public class FakeReservationRepository implements ReservationRepositoryPort {

    private final Map<UUID, Reservation> reservations = new HashMap<>();

    @Override
    public Mono<Reservation> save(Reservation reservation) {
        reservations.put(reservation.getId(), reservation);
        return Mono.just(reservation);
    }

    @Override
    public Mono<Reservation> findById(UUID id) {
        return Mono.justOrEmpty(reservations.get(id));
    }

    @Override
    public Mono<Reservation> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public Flux<Reservation> findAllByTouristId(UUID touristId, int limit) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> touristId.equals(reservation.getTouristId()));
    }

    @Override
    public Flux<Reservation> findAllByStatus(ReservationStatus status, int limit) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> status == reservation.getStatus());
    }

    @Override
    public Flux<Reservation> findAllByProviderId(UUID providerId, ReservationStatus status, int limit) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> providerId.equals(reservation.getProviderId()));
    }

    @Override
    public Flux<Reservation> findAllByGuideId(UUID guideId, ReservationStatus status, int limit) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> guideId.equals(reservation.getGuideId()));
    }

    @Override
    public Flux<Reservation> findAllByItem(UUID itemId, ReservationItemType type, int limit) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> itemId.equals(reservation.getItemId()) && type == reservation.getItemType());
    }

    @Override
    public Mono<Integer> sumActivePeopleForItem(UUID itemId, ReservationItemType type) {
        return findAllByItem(itemId, type, Integer.MAX_VALUE)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED)
                .map(Reservation::getNumberOfPeople)
                .reduce(0, Integer::sum);
    }

    @Override
    public Mono<Long> countActiveByTourist(UUID touristId) {
        return findAllByTouristId(touristId, Integer.MAX_VALUE)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED)
                .count();
    }

    @Override
    public Mono<Void> lockTourist(UUID touristId) {
        return Mono.empty();
    }

    @Override
    public Mono<Integer> rescheduleItem(UUID itemId, ReservationItemType type, Instant newStartsAt) {
        return Mono.just(0);
    }
}
