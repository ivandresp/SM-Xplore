package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

import java.time.Instant;
import java.util.List;

/**
 * Resultado del filtro automático de reseñas. Se conserva en la reseña para que el
 * administrador pueda revisar la decisión del modelo sin volver a consultarlo y para
 * alimentar la cola de reseñas pendientes de moderación.
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
            throw new InvalidCatalogException("review AI assessment status is required");
        }
        if (checkedAt == null) {
            throw new InvalidCatalogException("review AI assessment checkedAt is required");
        }
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new InvalidCatalogException("review AI assessment score must be between 0 and 1");
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

    public boolean hasAssessment() {
        return status == Status.ASSESSED;
    }
}
