package com.smxplore.activitiesevents.application.reservations;

import com.smxplore.activitiesevents.application.outbox.ReservationEventFactory;
import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationNoShowUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RegisterReservationNoShowUseCase implements RegisterReservationNoShowUseCasePort {
    private final ReservationRepositoryPort reservationRepo;
    private final OutboxPort outbox;
    private final ReservationEventFactory eventFactory;

    @Override
    public Mono<Reservation> handle(UUID reservationId) {
        return reservationRepo.findByIdForUpdate(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                .doOnNext(reservation -> reservation.registerNoShow(Instant.now()))
                .flatMap(reservationRepo::save)
                .flatMap(saved -> eventFactory.reservationChanged(saved)
                        .flatMap(outbox::enqueue)
                        .thenReturn(saved));
    }
}
