package com.smxplore.activitiesevents.infrastructure.transaction;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Wraps the reservation creation in an R2DBC transaction: the item row lock,
 * the capacity check and the insert happen atomically.
 * <p>
 * The unique index on (tourist_id, idempotency_key) is the final guard against
 * two concurrent retries. The loser of that race reads the winner's row after
 * the transaction rolls back, so it never consumes capacity twice.
 * <p>
 * R2DBC does not translate driver errors into Spring's DataAccessException
 * hierarchy: a duplicate key arrives as {@link R2dbcDataIntegrityViolationException}
 * with SQLSTATE 23505.
 */
@RequiredArgsConstructor
public class TransactionalCreateReservationUseCase implements CreateReservationUseCasePort {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final CreateReservationUseCasePort delegate;
    private final TransactionalOperator transactionalOperator;
    private final ReservationRepositoryPort reservationRepo;

    @Override
    public Mono<Reservation> handle(Reservation reservation) {
        return transactionalOperator.transactional(delegate.handle(reservation))
                .onErrorResume(R2dbcDataIntegrityViolationException.class,
                        error -> replayAfterDuplicate(reservation, error));
    }

    private Mono<Reservation> replayAfterDuplicate(Reservation reservation,
                                                   R2dbcDataIntegrityViolationException error) {
        if (reservation.getIdempotencyKey() == null
                || !UNIQUE_VIOLATION_SQL_STATE.equals(error.getSqlState())) {
            return Mono.error(error);
        }
        return reservationRepo.findByTouristIdAndIdempotencyKey(
                        reservation.getTouristId(), reservation.getIdempotencyKey())
                .switchIfEmpty(Mono.error(error));
    }
}
