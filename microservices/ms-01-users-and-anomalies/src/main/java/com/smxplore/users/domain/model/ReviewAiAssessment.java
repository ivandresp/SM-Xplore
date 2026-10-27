package com.smxplore.users.domain.model;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Resultado del filtro automático de reseñas. Se conserva en la reseña para que el administrador pueda revisar la
 * decisión del modelo sin volver a consultarlo.
 */
public record ReviewAiAssessment(
        Status status,
        boolean flagged,
        double score,
        List<String> categories,
        String reason,
        String model,
        Instant checkedAt) {

    public enum Status {
        ASSESSED,
        SKIPPED,
        UNAVAILABLE
    }

    public ReviewAiAssessment {
        Objects.requireNonNull(status, "El estado del análisis es obligatorio");
        Objects.requireNonNull(checkedAt, "La fecha del análisis es obligatoria");
        if (status == Status.ASSESSED && (score < 0 || score > 1 || Double.isNaN(score))) {
            throw new ValidationException("La puntuación del filtro debe estar entre 0 y 1");
        }
        categories = categories == null
                ? List.of()
                : categories.stream()
                        .filter(category -> category != null && !category.isBlank())
                        .map(String::trim)
                        .toList();
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public static ReviewAiAssessment assessed(
            boolean flagged, double score, List<String> categories, String reason, String model, Instant now) {
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
