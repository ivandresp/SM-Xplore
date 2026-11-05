package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByStatusUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ConsultReservationsByStatusUseCase implements ConsultReservationsByStatusUseCasePort {
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Flux<Reservation> handle(ReservationStatus status, int limit) {
        return reservationRepo.findAllByStatus(status, limit);
    }
}
