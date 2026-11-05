package com.smxplore.activitiesevents.domain.model.review;

/**
 * Visibility of a review. The names are the ones in
 * {@code contracts/events/reviews.review.changed.v1.json}.
 *
 * <p>ms-03 has no moderation filter of its own, so its reviews are born {@link #PUBLISHED}.
 * {@link #PENDING_REVIEW} belongs to reviews retained by the ms-04 AI filter, and
 * {@link #HIDDEN}/{@link #DELETED} to moderation outcomes; ms-03 can represent them but does not
 * produce them yet.
 */
public enum ReviewStatus {
    PUBLISHED,
    PENDING_REVIEW,
    HIDDEN,
    DELETED
}
