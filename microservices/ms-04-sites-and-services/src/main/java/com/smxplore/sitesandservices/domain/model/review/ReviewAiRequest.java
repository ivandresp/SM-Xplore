package com.smxplore.sitesandservices.domain.model.review;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;

/**
 * Texto mínimo que necesita el filtro para evaluar una reseña. No incluye datos
 * personales: solo el tipo de destino, la calificación y el comentario público.
 */
public record ReviewAiRequest(String targetType, int rating, String comment) {

    public ReviewAiRequest {
        if (targetType == null || targetType.isBlank()) {
            throw new InvalidCatalogException("review AI request targetType is required");
        }
        if (rating < 1 || rating > 5) {
            throw new InvalidCatalogException("review AI request rating must be between 1 and 5");
        }
    }
}
