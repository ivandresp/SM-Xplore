package com.smxplore.activitiesevents.domain.model.review;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
@Getter
public class Review {

    private static final int COMMENT_MAX_LENGTH = 1000;

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID itemId;
    private final ReviewItemType itemType;
    private final ReviewUser user;
    private final int rating;
    private final String comment;
    private final ReviewReply reply;

    @Builder.Default
    private final ReviewStatus status = ReviewStatus.PUBLISHED;

    /** Result of the automatic moderation filter; {@code null} on legacy documents. */
    private final ReviewAiAssessment aiAssessment;

    /** True when the automatic filter retained the review (pending review or hidden). */
    @Builder.Default
    private final boolean aiRetained = false;

    /** Set on every change; {@link #updatedAt()} falls back to {@code createdAt} for old documents. */
    private final Instant updatedAt;

    @Builder.Default
    private final Instant createdAt = Instant.now();

    public void validate() {
        if (itemId == null) {
            throw new InvalidReviewException("Item id is mandatory.");
        }
        if (itemType == null) {
            throw new InvalidReviewException("Item type is mandatory.");
        }
        if (user == null || user.id() == null || user.name() == null || user.name().isBlank()) {
            throw new InvalidReviewException("Review user is mandatory.");
        }
        if (rating < 1 || rating > 5) {
            throw new InvalidReviewException("Rating must be between 1 and 5.");
        }
        if (comment != null && comment.length() > COMMENT_MAX_LENGTH) {
            throw new InvalidReviewException("Comment must not exceed %d characters.".formatted(COMMENT_MAX_LENGTH));
        }
        if (status == null) {
            throw new InvalidReviewException("Review status is mandatory.");
        }
        if (aiRetained && aiAssessment == null) {
            throw new InvalidReviewException("A retained review requires an AI assessment.");
        }
    }

    /**
     * Stores the filter verdict and, when the policy flags the review with enough confidence, leaves
     * it pending or hides it. Reviews are immutable, so a new instance is returned.
     */
    public Review withAiAssessment(ReviewAiAssessment assessment, ReviewAiPolicy policy, Instant now) {
        if (assessment == null) {
            throw new InvalidReviewException("Review AI assessment is mandatory.");
        }
        if (policy == null) {
            throw new InvalidReviewException("Review AI policy is mandatory.");
        }
        boolean retained = policy.shouldFlag(assessment);
        var builder = toBuilder()
                .aiAssessment(assessment)
                .aiRetained(retained);
        if (retained) {
            builder.status(policy.flaggedStatus())
                    .updatedAt(now);
        }
        var updated = builder.build();
        updated.validate();
        return updated;
    }

    /**
     * @return when the review last changed. Documents written before the field existed fall back to
     *     {@code createdAt}, so consumers never see a missing timestamp.
     */
    public Instant updatedAt() {
        return updatedAt != null ? updatedAt : createdAt;
    }

    public Review withReply(ReviewReply reply) {
        return toBuilder().reply(reply).updatedAt(Instant.now()).build();
    }

    public Review withStatus(ReviewStatus newStatus) {
        return toBuilder().status(newStatus).updatedAt(Instant.now()).build();
    }
}
