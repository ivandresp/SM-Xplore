package com.smxplore.activitiesevents.domain.model.review;

import java.util.Objects;

/**
 * Decides whether a review flagged by the AI filter stays pending review or is hidden at once, and
 * whether a filter outage must reject the creation. Same semantics as ms-04.
 */
public record ReviewAiPolicy(double threshold, ReviewAiAction action, boolean failOpen) {

    public ReviewAiPolicy {
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("Review AI threshold must be between 0 and 1.");
        }
        Objects.requireNonNull(action, "Review AI action is required.");
    }

    public boolean shouldFlag(ReviewAiAssessment assessment) {
        return assessment != null && assessment.isFlagged() && assessment.score() >= threshold;
    }

    public boolean shouldRejectOnFailure(ReviewAiAssessment assessment) {
        return assessment != null && assessment.isUnavailable() && !failOpen;
    }

    public ReviewStatus flaggedStatus() {
        return action == ReviewAiAction.HIDE ? ReviewStatus.HIDDEN : ReviewStatus.PENDING_REVIEW;
    }
}
