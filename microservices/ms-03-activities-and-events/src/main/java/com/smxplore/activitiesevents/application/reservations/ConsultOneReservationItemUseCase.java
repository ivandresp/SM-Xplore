package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationItemNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationItemUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultOneReservationItemUseCase implements ConsultOneReservationItemUseCasePort {
    private final ReservationItemRepositoryPort reservationItemRepo;

    @Override
    public Mono<ReservationItem> handle(UUID reservationItemId) {
        return reservationItemRepo.findById(reservationItemId)
                .switchIfEmpty(Mono.error(new ReservationItemNotFoundException(reservationItemId)));
    }
}
