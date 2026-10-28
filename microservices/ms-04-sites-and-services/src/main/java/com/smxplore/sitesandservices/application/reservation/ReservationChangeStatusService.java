package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.ReservationAccess;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ReservationConflictException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReservationStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationOutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationTransactionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReservationChangeStatusService implements ChangeReservationStatusUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final ReservationTransactionPort transaction;
    private final ReservationOutboxPort outbox;
    private final ReservationAccess access;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Reservation> handle(
            UUID reservationId,
            ReservationStatus status,
            String reason,
            Actor actor,
            String correlationId
    ) {
        if (reservationId == null) {
            return Mono.error(new InvalidCatalogException("reservationId is required"));
        }
        if (status == null) {
            return Mono.error(new InvalidCatalogException("reservation status is required"));
        }
        if (status != ReservationStatus.CANCELLED
                && status != ReservationStatus.COMPLETED) {
            return Mono.error(new InvalidCatalogException(
                    "Reservations are confirmed automatically; only CANCELLED or COMPLETED can be requested"));
        }
        if (status != ReservationStatus.CANCELLED
                && reason != null && !reason.isBlank()) {
            return Mono.error(new InvalidCatalogException(
                    "reason is only allowed when cancelling a reservation"));
        }

        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Reservation", reservationId)))
                .flatMap(reservation -> access.requireStatusChange(reservation, status, actor))
                .flatMap(reservation -> change(reservation, status, reason, correlationId));
    }

    private Mono<Reservation> change(
            Reservation reservation,
            ReservationStatus status,
            String reason,
            String correlationId
    ) {
        if (reservation.status() == status) {
            return Mono.just(reservation);
        }
        Instant now = Instant.now(clock);
        if (status == ReservationStatus.COMPLETED && reservation.bookedFor().isAfter(now)) {
            return Mono.error(new ReservationConflictException(
                    "A reservation cannot be completed before its booked date"));
        }

        Reservation updated = reservation.changeStatus(status, now, reason);
        updated.validate();
        return transaction.execute(Mono.defer(() -> reservationRepository
                .updateStatus(updated, reservation.status())
                .flatMap(applied -> {
                    if (!applied) {
                        return Mono.error(new ReservationConflictException(
                                "Reservation was modified concurrently; refresh and retry"));
                    }
                    return outbox.append(
                                    eventFactory.reservationChanged(updated, correlationId))
                            .thenReturn(updated);
                })));
    }
}
