package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByStatusUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ConsultReservationsByStatusUseCase implements ConsultReservationsByStatusUseCasePort {
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<PageResult<Reservation>> handle(ReservationStatus status, int page, int size) {
        return reservationRepo.findPageByStatus(status, page, size);
    }
}
