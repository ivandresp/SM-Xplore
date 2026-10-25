package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationRequestException;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CreateReservationUseCase implements CreateReservationUseCasePort {
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Reservation> handle(Reservation reservation) {
        return Mono.justOrEmpty(reservation)
                .switchIfEmpty(Mono.error(new InvalidReservationRequestException("Reservation cannot be null")))
                .doOnNext(Reservation::validate)
                .flatMap(reservationRepo::save);
    }
}
