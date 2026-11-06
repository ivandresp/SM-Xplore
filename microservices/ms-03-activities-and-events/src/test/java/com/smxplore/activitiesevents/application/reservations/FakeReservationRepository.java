package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationCloseCandidate;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
    public Mono<Reservation> findByTouristIdAndIdempotencyKey(UUID touristId, String idempotencyKey) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> touristId.equals(reservation.getTouristId())
                        && idempotencyKey.equals(reservation.getIdempotencyKey()))
                .next();
    }

    @Override
    public Flux<Reservation> findAllByTouristId(UUID touristId, int limit) {
        return Flux.fromIterable(reservations.values())
                .filter(reservation -> touristId.equals(reservation.getTouristId()));
    }

    @Override
    public Mono<PageResult<Reservation>> findPageByStatus(ReservationStatus status, int page, int size) {
        return Mono.just(page(reservations.values().stream()
                .filter(reservation -> status == reservation.getStatus())
                .toList(), page, size));
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
    public Mono<PageResult<Reservation>> findPageByItemRefs(Collection<UUID> eventIds, Collection<UUID> activityIds,
                                                            ReservationStatus status, int page, int size) {
        return Mono.just(page(reservations.values().stream()
                .filter(reservation -> reservation.getStatus() == status)
                .filter(reservation -> reservation.getItemType() == ReservationItemType.EVENT
                        ? eventIds.contains(reservation.getItemId())
                        : activityIds.contains(reservation.getItemId()))
                .toList(), page, size));
    }

    private static PageResult<Reservation> page(List<Reservation> matches, int page, int size) {
        int from = Math.min(page * size, matches.size());
        int to = Math.min(from + size, matches.size());
        return new PageResult<>(List.copyOf(matches.subList(from, to)), page, size, matches.size());
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

    @Override
    public Flux<ReservationCloseCandidate> findStaleConfirmed(Instant cutoff) {
        return Flux.empty();
    }
}
