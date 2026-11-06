package com.smxplore.sitesandservices.domain.model.review;

import java.util.Objects;

/**
 * Decide si una reseña marcada por el filtro de IA queda pendiente de revisión o se
 * oculta inmediatamente, y si una caída del filtro debe rechazar la creación.
 */
public record ReviewAiPolicy(double threshold, ReviewAiAction action, boolean failOpen) {

    public ReviewAiPolicy {
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("review AI threshold must be between 0 and 1");
        }
        Objects.requireNonNull(action, "review AI action is required");
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
