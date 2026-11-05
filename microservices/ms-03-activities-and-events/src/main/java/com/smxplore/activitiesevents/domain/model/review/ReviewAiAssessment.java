package com.smxplore.activitiesevents.domain.model.review;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;

import java.time.Instant;
import java.util.List;

/**
 * Result of the automatic review filter. It is stored with the review so an administrator can
 * audit the model decision without querying it again and so the moderation queue knows why a
 * review was retained.
 */
public record ReviewAiAssessment(
        Status status,
        boolean flagged,
        double score,
        List<String> categories,
        String reason,
        String model,
        Instant checkedAt
) {

    public enum Status {
        ASSESSED,
        SKIPPED,
        UNAVAILABLE
    }

    public ReviewAiAssessment {
        if (status == null) {
            throw new InvalidReviewException("Review AI assessment status is required.");
        }
        if (checkedAt == null) {
            throw new InvalidReviewException("Review AI assessment checkedAt is required.");
        }
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new InvalidReviewException("Review AI assessment score must be between 0 and 1.");
        }
        categories = categories == null
                ? List.of()
                : categories.stream()
                        .filter(category -> category != null && !category.isBlank())
                        .map(String::trim)
                        .toList();
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        model = model == null || model.isBlank() ? null : model.trim();
    }

    public static ReviewAiAssessment assessed(
            boolean flagged,
            double score,
            List<String> categories,
            String reason,
            String model,
            Instant now
    ) {
        return new ReviewAiAssessment(Status.ASSESSED, flagged, score, categories, reason, model, now);
    }

    public static ReviewAiAssessment skipped(Instant now) {
        return new ReviewAiAssessment(Status.SKIPPED, false, 0, List.of(), null, null, now);
    }

    public static ReviewAiAssessment unavailable(Instant now) {
        return new ReviewAiAssessment(Status.UNAVAILABLE, false, 0, List.of(), null, null, now);
    }

    public boolean isFlagged() {
        return status == Status.ASSESSED && flagged;
    }

    public boolean isUnavailable() {
        return status == Status.UNAVAILABLE;
    }
}
