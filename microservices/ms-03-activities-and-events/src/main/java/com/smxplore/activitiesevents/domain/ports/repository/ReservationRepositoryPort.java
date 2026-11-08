package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationCloseCandidate;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ReservationRepositoryPort {
    Mono<Reservation> save(Reservation reservation);

    Mono<Reservation> findById(UUID id);

    /** Looks up the reservation that already consumed a client retry token. */
    Mono<Reservation> findByTouristIdAndIdempotencyKey(UUID touristId, String idempotencyKey);

    /**
     * Lectura con lock pesimista de la fila (SELECT ... FOR UPDATE) para
     * serializar las operaciones que mutan la reserva. Debe ejecutarse dentro
     * de una transacción; de lo contrario el lock se libera de inmediato.
     */
    Mono<Reservation> findByIdForUpdate(UUID id);

    Flux<Reservation> findAllByTouristId(UUID touristId, int limit);

    /** One page of reservations in a status; the database applies the offset and returns the total. */
    Mono<PageResult<Reservation>> findPageByStatus(ReservationStatus status, int page, int size);

    /** Lists the reservations received by a provider; {@code status} is optional (null = any). */
    Flux<Reservation> findAllByProviderId(UUID providerId, ReservationStatus status, int limit);

    /** Lists the reservations received by a guide; {@code status} is optional (null = any). */
    Flux<Reservation> findAllByGuideId(UUID guideId, ReservationStatus status, int limit);

    /** Lists the reservations of a given event or activity. */
    Flux<Reservation> findAllByItem(UUID itemId, ReservationItemType type, int limit);

    /** One page of reservations for any of the manager's events or activities. */
    Mono<PageResult<Reservation>> findPageByItemRefs(Collection<UUID> eventIds, Collection<UUID> activityIds,
                                                     ReservationStatus status, int page, int size);

    /** People already booked (CONFIRMED) for an event or activity. */
    Mono<Integer> sumActivePeopleForItem(UUID itemId, ReservationItemType type);

    /**
     * Reserva CONFIRMED o COMPLETED del turista para el item: solo quien ha reservado (o asistido)
     * puede resenar, para que la resena acredite una experiencia real.
     */
    Mono<Boolean> existsReservationForReview(UUID touristId, UUID itemId, ReservationItemType type);

    /** CONFIRMED reservations of a tourist whose item has not started yet. */
    Mono<Long> countActiveByTourist(UUID touristId);

    /** Serializes the active-reservation cap for one tourist, inside the current transaction. */
    Mono<Void> lockTourist(UUID touristId);

    /** Keeps open reservations aligned when the item schedule changes. */
    Mono<Integer> rescheduleItem(UUID itemId, ReservationItemType type, Instant newStartsAt);

    /**
     * CONFIRMED reservations to close: published items ended before {@code cutoff} become NO_SHOW,
     * and cancelled items become CANCELLED. The caller mutates and publishes each one.
     */
    Flux<ReservationCloseCandidate> findStaleConfirmed(Instant cutoff);
}
