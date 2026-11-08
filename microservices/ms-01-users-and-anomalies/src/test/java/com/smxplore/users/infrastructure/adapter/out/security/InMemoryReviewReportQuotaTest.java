package com.smxplore.users.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.exception.ReportQuotaExceededException;
import com.smxplore.users.infrastructure.config.ReviewReportProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * MS01-22: la cuota de reportes se agota tras el máximo configurado y se reinicia al terminar la ventana.
 */
class InMemoryReviewReportQuotaTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final UUID TOURIST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryReviewReportQuota quota =
            new InMemoryReviewReportQuota(new ReviewReportProperties(2, Duration.ofHours(1)), clock);

    @Test
    void allowsReportsUntilQuotaIsReached() {
        StepVerifier.create(quota.checkAndRecord(TOURIST_ID)).verifyComplete();
        StepVerifier.create(quota.checkAndRecord(TOURIST_ID)).verifyComplete();

        StepVerifier.create(quota.checkAndRecord(TOURIST_ID))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ReportQuotaExceededException.class)
                        .extracting(exception -> ((ReportQuotaExceededException) exception).retryAfter())
                        .satisfies(
                                retryAfter -> assertThat((Duration) retryAfter).isPositive()))
                .verify();
    }

    @Test
    void resetsQuotaAfterWindowExpires() {
        StepVerifier.create(quota.checkAndRecord(TOURIST_ID)).verifyComplete();
        StepVerifier.create(quota.checkAndRecord(TOURIST_ID)).verifyComplete();
        StepVerifier.create(quota.checkAndRecord(TOURIST_ID))
                .expectError(ReportQuotaExceededException.class)
                .verify();

        clock.advance(Duration.ofHours(1).plusSeconds(1));

        StepVerifier.create(quota.checkAndRecord(TOURIST_ID)).verifyComplete();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
