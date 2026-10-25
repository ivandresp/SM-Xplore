package com.smxplore.activitiesevents.infrastructure.transaction;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemsUseCasePort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.UUID;

/**
 * Wraps the use case in an R2DBC transaction: saving the items and updating the
 * reservation total happen atomically (a failure leaves nothing half-done).
 * Items are only emitted after the commit succeeded.
 * <p>
 * It is implemented as an infrastructure decorator so the use case (application
 * layer) keeps not depending on Spring.
 */
@RequiredArgsConstructor
public class TransactionalAddReservationItemsUseCase implements AddReservationItemsUseCasePort {

    private final AddReservationItemsUseCasePort delegate;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Flux<ReservationItem> handle(UUID reservationId, Collection<ReservationItem> items) {
        // collectList() inside the transaction: items are emitted only after the
        // commit succeeded (the client never sees half-done results)
        return transactionalOperator.transactional(delegate.handle(reservationId, items).collectList())
                .flatMapMany(Flux::fromIterable);
    }
}
