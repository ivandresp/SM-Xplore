package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CancelReservationUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CancelReservationUseCase implements CancelReservationUseCasePort {
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Reservation> handle(UUID reservationId, boolean staff) {
        return reservationRepo.findByIdForUpdate(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                .doOnNext(reservation -> {
                    if (staff) {
                        reservation.cancel();
                    } else {
                        reservation.cancelAsTourist(Instant.now());
                    }
                })
                .flatMap(reservationRepo::save);
    }
}
