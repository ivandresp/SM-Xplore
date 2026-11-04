package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.domain.exception.ReservationConflictException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReservationStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ReservationCompletionService {

    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Actor SYSTEM_ACTOR =
            new Actor(SYSTEM_ACTOR_ID, UserRole.ADMIN, "System");

    private final ReservationRepositoryPort reservationRepository;
    private final ChangeReservationStatusUseCase changeStatusUseCase;
    private final int batchSize;
    private final Clock clock;

    public Mono<Void> completeOnce() {
        Instant now = Instant.now(clock);
        return reservationRepository.findCompletable(now, batchSize)
                .concatMap(reservationId -> changeStatusUseCase.handle(
                                reservationId,
                                ReservationStatus.COMPLETED,
                                null,
                                SYSTEM_ACTOR,
                                "system:auto-completion"
                        )
                        .doOnSuccess(reservation -> log.info(
                                "Reservation {} auto-completed", reservation.reservationId()))
                        .onErrorResume(ReservationConflictException.class, error -> {
                            log.warn("Reservation {} could not be auto-completed: {}",
                                    reservationId, error.getMessage());
                            return Mono.empty();
                        })
                        .onErrorResume(error -> {
                            log.error("Unexpected error auto-completing reservation {}",
                                    reservationId, error);
                            return Mono.empty();
                        }))
                .then();
    }
}
