package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByGuideUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReservationsByGuideUseCase implements ConsultReservationsByGuideUseCasePort {

    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Flux<Reservation> handle(UUID guideId, ReservationStatus status, int limit) {
        return reservationRepo.findAllByGuideId(guideId, status, limit);
    }
}
