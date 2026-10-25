package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReservationItemsUseCase implements ConsultReservationItemsUseCasePort {
    private final ReservationRepositoryPort reservationRepo;
    private final ReservationItemRepositoryPort reservationItemRepo;

    @Override
    public Flux<ReservationItem> handle(UUID reservationId) {
        return reservationRepo.findById(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                .flatMapMany(reservation -> reservationItemRepo.findByReservationId(reservationId));
    }
}
