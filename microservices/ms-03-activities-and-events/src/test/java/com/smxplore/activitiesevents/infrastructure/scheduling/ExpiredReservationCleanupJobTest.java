package com.smxplore.activitiesevents.infrastructure.scheduling;

import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiredReservationCleanupJobTest {

    @Mock
    private ReservationRepositoryPort reservationRepo;

    @Test
    void cleansStaleReservationsUsingTheConfiguredGracePeriod() {
        var grace = Duration.ofHours(24);
        var job = new ExpiredReservationCleanupJob(reservationRepo, grace);
        var before = Instant.now().minus(grace);
        given(reservationRepo.closeStaleConfirmed(any())).willReturn(Mono.just(3));

        StepVerifier.create(job.closeExpired())
                .verifyComplete();

        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(reservationRepo).closeStaleConfirmed(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                before.minusSeconds(1),
                Instant.now().minus(grace).plusSeconds(1));
    }

    @Test
    void rejectsNonPositiveGrace() {
        assertThatThrownBy(() -> new ExpiredReservationCleanupJob(reservationRepo, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
