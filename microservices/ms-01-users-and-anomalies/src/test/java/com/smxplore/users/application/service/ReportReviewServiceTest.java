package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReportReviewCommand;
import com.smxplore.users.application.dto.ReviewStatistics;
import com.smxplore.users.application.port.out.ReviewReportQuota;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ReportQuotaExceededException;
import com.smxplore.users.domain.model.ReputationSummary;
import com.smxplore.users.domain.model.Review;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * MS01-22: un turista solo puede reportar dentro de su cuota; al agotarla no se guarda el reporte.
 */
class ReportReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID TOURIST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GUIDE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FakeReviewRepository reviews = new FakeReviewRepository();
    private final RecordingReviewReportQuota quota = new RecordingReviewReportQuota();

    @Test
    void recordsReportWhenQuotaAllowsIt() {
        Review review = Review.create(TOURIST_ID, ReviewTargetType.GUIDE, GUIDE_ID, 5, "Excelente", NOW);
        reviews.review = review;

        StepVerifier.create(service().report(TOURIST_ID, review.id(), new ReportReviewCommand("Lenguaje ofensivo")))
                .verifyComplete();

        assertThat(review.isReported()).isTrue();
        assertThat(reviews.saved).containsExactly(review);
        assertThat(quota.consumed).containsExactly(TOURIST_ID);
    }

    @Test
    void rejectsReportWhenQuotaIsExhausted() {
        Review review = Review.create(TOURIST_ID, ReviewTargetType.GUIDE, GUIDE_ID, 5, "Excelente", NOW);
        reviews.review = review;
        quota.exhausted = true;

        StepVerifier.create(service().report(TOURIST_ID, review.id(), new ReportReviewCommand("Spam")))
                .expectError(ReportQuotaExceededException.class)
                .verify();

        assertThat(reviews.saved).isEmpty();
        assertThat(review.isReported()).isFalse();
    }

    private ReportReviewService service() {
        return new ReportReviewService(reviews, quota, CLOCK);
    }

    private static final class RecordingReviewReportQuota implements ReviewReportQuota {

        private final List<UUID> consumed = new ArrayList<>();
        private boolean exhausted;

        @Override
        public Mono<Void> checkAndRecord(UUID reporterId) {
            if (exhausted) {
                return Mono.error(new ReportQuotaExceededException(Duration.ofHours(1)));
            }
            consumed.add(reporterId);
            return Mono.empty();
        }
    }

    private static final class FakeReviewRepository implements ReviewRepository {

        private final List<Review> saved = new ArrayList<>();
        private Review review;

        @Override
        public Mono<Review> save(Review value) {
            saved.add(value);
            return Mono.just(value);
        }

        @Override
        public Mono<Review> findById(String reviewId) {
            return Mono.justOrEmpty(review).filter(candidate -> candidate.id().equals(reviewId));
        }

        @Override
        public Mono<Boolean> existsByTouristAndTarget(UUID touristId, ReviewTargetType targetType, UUID targetId) {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }

        @Override
        public Mono<PageResult<Review>> findVisible(ReviewTargetType targetType, UUID targetId, int page, int size) {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }

        @Override
        public Mono<PageResult<Review>> findReported(int page, int size) {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }

        @Override
        public Mono<PageResult<Review>> findAiFlagged(int page, int size) {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }

        @Override
        public Mono<ReputationSummary> summary(ReviewTargetType targetType, UUID targetId) {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }

        @Override
        public Mono<ReviewStatistics> statistics() {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }
    }
}
