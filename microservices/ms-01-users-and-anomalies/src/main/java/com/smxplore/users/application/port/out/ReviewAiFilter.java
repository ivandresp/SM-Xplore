package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.ReviewAiRequest;
import com.smxplore.users.domain.model.ReviewAiAssessment;
import reactor.core.publisher.Mono;

public interface ReviewAiFilter {

    /**
     * Analiza el texto de una reseña. Nunca debe fallar: si el servicio no está disponible devuelve un resultado
     * {@code UNAVAILABLE} para que la política configurada decida qué hacer.
     */
    Mono<ReviewAiAssessment> assess(ReviewAiRequest request);
}
