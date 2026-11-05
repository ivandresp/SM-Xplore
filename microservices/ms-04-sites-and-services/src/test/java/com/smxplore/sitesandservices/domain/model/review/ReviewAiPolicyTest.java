package com.smxplore.sitesandservices.domain.model.review;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewAiPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void flaggedReviewWithEnoughConfidenceGoesToPendingReview() {
        Review review = review();
        ReviewAiAssessment assessment =
                ReviewAiAssessment.assessed(true, 0.91, List.of("COORDINADA"), "campaña", "littlellm", NOW);

        Review updated = review.withAiAssessment(
                assessment,
                new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true),
                NOW.plusSeconds(1));

        assertThat(updated.status()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(updated.aiAssessment()).isEqualTo(assessment);
        assertThat(updated.aiRetained()).isTrue();
        assertThat(updated.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void lowConfidenceKeepsTheReviewPublished() {
        Review review = review();
        ReviewAiAssessment assessment =
                ReviewAiAssessment.assessed(true, 0.4, List.of("GENERICA"), null, "littlellm", NOW);

        Review updated = review.withAiAssessment(
                assessment,
                new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true),
                NOW);

        assertThat(updated.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(updated.aiAssessment()).isEqualTo(assessment);
        assertThat(updated.aiRetained()).isFalse();
    }

    @Test
    void hideActionHidesTheReviewImmediately() {
        Review review = review();
        ReviewAiAssessment assessment =
                ReviewAiAssessment.assessed(true, 0.95, List.of("EXTORSION"), null, "littlellm", NOW);

        Review updated = review.withAiAssessment(
                assessment,
                new ReviewAiPolicy(0.7, ReviewAiAction.HIDE, true),
                NOW);

        assertThat(updated.status()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(updated.aiRetained()).isTrue();
    }

    @Test
    void unavailableResultOnlyRejectsWhenPolicyIsFailClosed() {
        ReviewAiAssessment unavailable = ReviewAiAssessment.unavailable(NOW);

        assertThat(new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true)
                .shouldRejectOnFailure(unavailable)).isFalse();
        assertThat(new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, false)
                .shouldRejectOnFailure(unavailable)).isTrue();
    }

    private static Review review() {
        return Review.builder()
                .reviewId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .itemType(com.smxplore.sitesandservices.domain.model.common.ItemType.SITE)
                .itemId(UUID.randomUUID())
                .itemName("Sitio")
                .rating(5)
                .commentary("Texto")
                .status(ReviewStatus.PUBLISHED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
