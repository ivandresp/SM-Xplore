package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByTouristUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReservationsByTouristUseCase implements ConsultReservationsByTouristUseCasePort {
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Flux<Reservation> handle(UUID touristId, int limit) {
        return reservationRepo.findAllByTouristId(touristId, limit);
    }
}
