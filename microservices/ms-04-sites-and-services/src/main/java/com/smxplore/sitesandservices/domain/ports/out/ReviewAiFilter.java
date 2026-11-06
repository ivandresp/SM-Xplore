package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.review.ReviewAiAssessment;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiRequest;
import reactor.core.publisher.Mono;

public interface ReviewAiFilter {

    /**
     * Analiza el texto de una reseña. Nunca debe fallar: si el servicio no está
     * disponible devuelve un resultado {@code UNAVAILABLE} para que la política
     * configurada decida qué hacer.
     */
    Mono<ReviewAiAssessment> assess(ReviewAiRequest request);
}
