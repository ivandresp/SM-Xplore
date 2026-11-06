package com.smxplore.activitiesevents.infrastructure.scheduling;

import com.smxplore.activitiesevents.application.outbox.ReservationEventFactory;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationCloseCandidate;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Closes stale CONFIRMED reservations: published items that ended longer ago
 * than the grace period become NO_SHOW, and cancelled items cancel their open
 * reservations. Each change is published like the manual transitions so the
 * tourist, guide and manager receive the corresponding notification.
 *
 * <p>Each close runs in its own transaction: the status change and the outbox row are committed
 * together, so a failed enqueue rolls the reservation back to {@code CONFIRMED} and the next run
 * retries it instead of leaving a closed reservation without its event.
 */
@Component
@Slf4j
public class ExpiredReservationCleanupJob {

    private final ReservationRepositoryPort reservationRepo;
    private final OutboxPort outbox;
    private final ReservationEventFactory eventFactory;
    private final TransactionalOperator transactionalOperator;
    private final Duration grace;

    public ExpiredReservationCleanupJob(ReservationRepositoryPort reservationRepo,
                                        OutboxPort outbox,
                                        ReservationEventFactory eventFactory,
                                        TransactionalOperator transactionalOperator,
                                        @Value("${app.reservations.auto-close.grace}") Duration grace) {
        if (grace == null || grace.isNegative() || grace.isZero()) {
            throw new IllegalArgumentException("app.reservations.auto-close.grace must be positive.");
        }
        this.reservationRepo = reservationRepo;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.transactionalOperator = transactionalOperator;
        this.grace = grace;
    }

    @Scheduled(cron = "${app.reservations.auto-close.cron}", zone = "America/Bogota")
    public Mono<Void> closeExpired() {
        var cutoff = Instant.now().minus(grace);
        return reservationRepo.findStaleConfirmed(cutoff)
                .concatMap(this::close)
                .count()
                .filter(count -> count > 0)
                .doOnNext(count -> log.info("Cleaned {} stale reservations", count))
                .then();
    }

    private Mono<Void> close(ReservationCloseCandidate candidate) {
        return transactionalOperator.transactional(closeOne(candidate))
                .onErrorResume(error -> {
                    // La transaccion revierte el cambio de estado: la reserva sigue CONFIRMED y la
                    // proxima ejecucion lo reintenta, en lugar de perder el evento.
                    log.warn("Could not close reservation {}: {}",
                            candidate.reservationId(), error.toString());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Reservation> closeOne(ReservationCloseCandidate candidate) {
        return reservationRepo.findByIdForUpdate(candidate.reservationId())
                .switchIfEmpty(Mono.empty())
                .flatMap(reservation -> {
                    var now = Instant.now();
                    if (candidate.targetStatus() == ReservationStatus.NO_SHOW) {
                        reservation.registerNoShow(now);
                    } else {
                        reservation.cancel();
                    }
                    return reservationRepo.save(reservation);
                })
                .flatMap(saved -> eventFactory.reservationChanged(saved)
                        .flatMap(outbox::enqueue)
                        .thenReturn(saved));
    }
}
