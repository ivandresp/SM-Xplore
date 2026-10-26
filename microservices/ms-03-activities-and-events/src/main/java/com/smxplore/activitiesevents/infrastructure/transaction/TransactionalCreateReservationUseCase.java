package com.smxplore.activitiesevents.infrastructure.transaction;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Wraps the reservation creation in an R2DBC transaction: the item row lock,
 * the capacity check and the insert happen atomically.
 */
@RequiredArgsConstructor
public class TransactionalCreateReservationUseCase implements CreateReservationUseCasePort {

    private final CreateReservationUseCasePort delegate;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Reservation> handle(Reservation reservation) {
        return transactionalOperator.transactional(delegate.handle(reservation));
    }
}
