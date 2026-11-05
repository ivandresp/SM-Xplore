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
}
