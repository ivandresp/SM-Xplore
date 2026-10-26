package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReservationRepositoryPort {
    Mono<Reservation> save(Reservation reservation);

    Mono<Reservation> findById(UUID id);

    /**
     * Lectura con lock pesimista de la fila (SELECT ... FOR UPDATE) para
     * serializar las operaciones que mutan la reserva. Debe ejecutarse dentro
     * de una transacción; de lo contrario el lock se libera de inmediato.
     */
    Mono<Reservation> findByIdForUpdate(UUID id);

    Flux<Reservation> findAllByTouristId(UUID touristId, int limit);

    Flux<Reservation> findAllByStatus(ReservationStatus status, int limit);

    /** Lists the reservations received by a provider; {@code status} is optional (null = any). */
    Flux<Reservation> findAllByProviderId(UUID providerId, ReservationStatus status, int limit);

    /** Lists the reservations received by a guide; {@code status} is optional (null = any). */
    Flux<Reservation> findAllByGuideId(UUID guideId, ReservationStatus status, int limit);

    /** Lists the reservations of a given event or activity. */
    Flux<Reservation> findAllByItem(UUID itemId, ReservationItemType type, int limit);

    /** People already booked (CONFIRMED) for an event or activity. */
    Mono<Integer> sumActivePeopleForItem(UUID itemId, ReservationItemType type);

    /** Number of CONFIRMED reservations of a tourist, for the anti-abuse cap. */
    Mono<Long> countActiveByTourist(UUID touristId);
}
