package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationAttendanceUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RegisterReservationAttendanceUseCase implements RegisterReservationAttendanceUseCasePort {
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Reservation> handle(UUID reservationId) {
        return reservationRepo.findByIdForUpdate(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                .doOnNext(reservation -> reservation.registerAttendance(Instant.now()))
                .flatMap(reservationRepo::save);
    }
}
