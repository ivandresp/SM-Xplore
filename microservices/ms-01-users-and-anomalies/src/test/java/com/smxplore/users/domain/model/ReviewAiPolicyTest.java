package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.policy.ReviewAiPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewAiPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void flaggedReviewWithEnoughConfidenceGoesToPendingReview() {
        Review review = review();
        ReviewAiAssessment assessment =
                ReviewAiAssessment.assessed(true, 0.91, List.of("COORDINADA"), "campaña", "littlellm", NOW);

        review.applyAiAssessment(assessment, new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true), NOW);

        assertThat(review.moderationStatus()).isEqualTo(ReviewModerationStatus.PENDIENTE_REVISION);
        assertThat(review.isVisible()).isFalse();
        assertThat(review.aiAssessment()).isEqualTo(assessment);
        assertThat(review.moderationReason()).isEqualTo("Filtro automático de reseñas");
    }

    @Test
    void lowConfidenceKeepsTheReviewVisible() {
        Review review = review();
        ReviewAiAssessment assessment = ReviewAiAssessment.assessed(true, 0.4, List.of("GENERICA"), null, "m", NOW);

        review.applyAiAssessment(assessment, new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true), NOW);

        assertThat(review.moderationStatus()).isEqualTo(ReviewModerationStatus.VISIBLE);
        assertThat(review.aiAssessment()).isEqualTo(assessment);
    }

    @Test
    void hideActionHidesTheReviewImmediately() {
        Review review = review();
        ReviewAiAssessment assessment = ReviewAiAssessment.assessed(true, 0.95, List.of("EXTORSION"), null, "m", NOW);

        review.applyAiAssessment(assessment, new ReviewAiPolicy(0.7, ReviewAiAction.HIDE, true), NOW);

        assertThat(review.moderationStatus()).isEqualTo(ReviewModerationStatus.HIDDEN);
    }

    @Test
    void unavailableResultOnlyRejectsWhenPolicyIsFailClosed() {
        ReviewAiAssessment unavailable = ReviewAiAssessment.unavailable(NOW);

        assertThat(new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true).shouldRejectOnFailure(unavailable))
                .isFalse();
        assertThat(new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, false).shouldRejectOnFailure(unavailable))
                .isTrue();
    }

    private static Review review() {
        return Review.create(UUID.randomUUID(), ReviewTargetType.PROVIDER, UUID.randomUUID(), 5, "texto", NOW);
    }
}
