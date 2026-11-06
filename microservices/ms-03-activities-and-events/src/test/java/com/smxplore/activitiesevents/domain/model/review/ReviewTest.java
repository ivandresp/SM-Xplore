package com.smxplore.activitiesevents.domain.model.review;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    @Test
    void reviewsAreBornPublished() {
        var review = review().build();

        review.validate();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void rejectsAReviewWithoutStatus() {
        var review = review().status(null).build();

        assertThatThrownBy(review::validate)
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("status");
    }

    @Test
    void fallsBackToTheCreationTimestamp() {
        var createdAt = Instant.parse("2026-11-01T10:00:00Z");
        var review = review().createdAt(createdAt).build();

        assertThat(review.updatedAt()).isEqualTo(createdAt);
    }

    @Test
    void changesTheTimestampWhenTheStatusChanges() {
        var createdAt = Instant.parse("2026-11-01T10:00:00Z");
        var review = review().createdAt(createdAt).build();

        var hidden = review.withStatus(ReviewStatus.HIDDEN);

        assertThat(hidden.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(hidden.updatedAt()).isAfter(createdAt);
    }

    @Test
    void changesTheTimestampWhenAReplyIsAdded() {
        var createdAt = Instant.parse("2026-11-01T10:00:00Z");
        var review = review().createdAt(createdAt).build();

        var replied = review.withReply(new ReviewReply("Gracias", UUID.randomUUID(), Instant.now()));

        assertThat(replied.getReply()).isNotNull();
        assertThat(replied.updatedAt()).isAfter(createdAt);
    }

    @Test
    void retainsAFlaggedReviewInPendingReview() {
        var review = review().build();
        var assessment = ReviewAiAssessment.assessed(true, 0.9, java.util.List.of("SPAM"),
                "spam", "littlellm", Instant.now());
        var policy = new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true);

        var assessed = review.withAiAssessment(assessment, policy, Instant.now());

        assertThat(assessed.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(assessed.isAiRetained()).isTrue();
        assertThat(assessed.getAiAssessment()).isEqualTo(assessment);
    }

    @Test
    void hidesAFlaggedReviewWhenThePolicyHides() {
        var review = review().build();
        var assessment = ReviewAiAssessment.assessed(true, 0.9, java.util.List.of("SPAM"),
                "spam", "littlellm", Instant.now());
        var policy = new ReviewAiPolicy(0.7, ReviewAiAction.HIDE, true);

        var assessed = review.withAiAssessment(assessment, policy, Instant.now());

        assertThat(assessed.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void keepsAnUnflaggedReviewPublished() {
        var review = review().build();
        var assessment = ReviewAiAssessment.assessed(false, 0.1, java.util.List.of(), null,
                "littlellm", Instant.now());
        var policy = new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true);

        var assessed = review.withAiAssessment(assessment, policy, Instant.now());

        assertThat(assessed.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(assessed.isAiRetained()).isFalse();
    }

    private static Review.ReviewBuilder review() {
        return Review.builder()
                .itemId(UUID.randomUUID())
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(5);
    }
}
