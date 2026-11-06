package com.smxplore.activitiesevents.domain.model.review;

/**
 * Visibility of a review. The names are the ones in
 * {@code contracts/events/reviews.review.changed.v1.json}.
 *
 * <p>{@link #PUBLISHED} is the only state that public listings, metrics and consumers count.
 * {@link #PENDING_REVIEW} retains reviews flagged by the automatic filter until an administrator
 * publishes or hides them; {@link #HIDDEN} and {@link #DELETED} are moderation outcomes.
 */
public enum ReviewStatus {
    PUBLISHED,
    PENDING_REVIEW,
    HIDDEN,
    DELETED
}
