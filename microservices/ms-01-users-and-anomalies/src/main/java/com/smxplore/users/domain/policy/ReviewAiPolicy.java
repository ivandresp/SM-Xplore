package com.smxplore.users.domain.policy;

import com.smxplore.users.domain.model.ReviewAiAction;
import com.smxplore.users.domain.model.ReviewAiAssessment;
import java.util.Objects;

/**
 * Decide si una reseña marcada por el filtro de IA se oculta o queda pendiente de revisión.
 */
public record ReviewAiPolicy(double threshold, ReviewAiAction action, boolean failOpen) {

    public ReviewAiPolicy {
        if (threshold <= 0 || threshold > 1 || Double.isNaN(threshold)) {
            throw new IllegalArgumentException("El umbral del filtro de IA debe estar entre 0 y 1");
        }
        Objects.requireNonNull(action, "La acción del filtro de IA es obligatoria");
    }

    public boolean shouldFlag(ReviewAiAssessment assessment) {
        return assessment.isFlagged() && assessment.score() >= threshold;
    }

    public boolean shouldRejectOnFailure(ReviewAiAssessment assessment) {
        return assessment.isUnavailable() && !failOpen;
    }
}
