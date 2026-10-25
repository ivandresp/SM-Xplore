package com.smxplore.activitiesevents.domain.model.review;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyReportedException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
@Getter
public class Review {

    private static final int COMMENT_MAX_LENGTH = 1000;
    private static final int REPORT_REASON_MAX_LENGTH = 500;

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID itemId;
    private final ReviewItemType itemType;
    private final ReviewUser user;
    private final int rating;
    private final String comment;
    private final ReviewReply reply;

    @Builder.Default
    private final boolean reported = false;
    private final String reportReason;
    private final Instant reportedAt;

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
    }

    public Review withReply(ReviewReply reply) {
        return toBuilder().reply(reply).build();
    }

    public Review withReport(String reason) {
        if (reported) {
            throw new ReviewAlreadyReportedException("This review was already reported.");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidReviewException("Report reason must not be blank.");
        }
        if (reason.length() > REPORT_REASON_MAX_LENGTH) {
            throw new InvalidReviewException(
                    "Report reason must not exceed %d characters.".formatted(REPORT_REASON_MAX_LENGTH));
        }
        return toBuilder()
                .reported(true)
                .reportReason(reason)
                .reportedAt(Instant.now())
                .build();
    }
}
