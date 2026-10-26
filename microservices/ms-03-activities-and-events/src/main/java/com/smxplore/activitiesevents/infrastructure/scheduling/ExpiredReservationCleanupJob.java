package com.smxplore.activitiesevents.infrastructure.scheduling;

import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Closes stale CONFIRMED reservations: published items that ended longer ago
 * than the grace period become NO_SHOW, and cancelled items cancel their open
 * reservations. Attendance is manual, so the grace period gives the guide time
 * to register it before the sweep.
 */
@Component
@Slf4j
public class ExpiredReservationCleanupJob {

    private final ReservationRepositoryPort reservationRepo;
    private final Duration grace;

    public ExpiredReservationCleanupJob(ReservationRepositoryPort reservationRepo,
                                        @Value("${app.reservations.auto-close.grace}") Duration grace) {
        if (grace == null || grace.isNegative() || grace.isZero()) {
            throw new IllegalArgumentException("app.reservations.auto-close.grace must be positive.");
        }
        this.reservationRepo = reservationRepo;
        this.grace = grace;
    }

    @Scheduled(cron = "${app.reservations.auto-close.cron}", zone = "America/Bogota")
    public Mono<Void> closeExpired() {
        var cutoff = Instant.now().minus(grace);
        return reservationRepo.closeStaleConfirmed(cutoff)
                .filter(count -> count > 0)
                .doOnNext(count -> log.info("Cleaned {} stale reservations", count))
                .then();
    }
}
