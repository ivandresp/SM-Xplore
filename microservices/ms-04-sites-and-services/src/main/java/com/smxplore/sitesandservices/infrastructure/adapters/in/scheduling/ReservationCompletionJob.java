package com.smxplore.sitesandservices.infrastructure.adapters.in.scheduling;

import com.smxplore.sitesandservices.application.reservation.ReservationCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.bookings.auto-complete-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationCompletionJob {

    private final ReservationCompletionService completionService;

    @Scheduled(fixedDelayString = "${app.bookings.completion-interval-ms:60000}")
    public void completePastReservations() {
        completionService.completeOnce()
                .doOnError(error -> log.error("Reservation auto-completion failed", error))
                .onErrorResume(error -> Mono.empty())
                .block(Duration.ofSeconds(60));
    }
}
