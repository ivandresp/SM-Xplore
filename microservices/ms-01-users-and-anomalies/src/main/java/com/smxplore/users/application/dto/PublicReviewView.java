package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Review;
import java.time.Instant;
import java.util.UUID;

/**
 * Vista pública de una reseña.
 *
 * <p>MS01-10: no incluye el identificador del turista. El listado de {@code /api/v1/public/reviews} es anónimo y antes
 * exponía {@code touristId}, que es el mismo valor que el claim {@code sub} del JWT: con él se podía reconstruir el
 * recorrido de una persona (destinos, fechas y calificaciones) y cruzar datos con otros microservicios, lo que
 * constituye un tratamiento de datos personales no necesario para mostrar reseñas.
 */
public record PublicReviewView(
        String id,
        String targetType,
        UUID targetId,
        int rating,
        String comment,
        ReviewResponseView response,
        Instant createdAt,
        Instant updatedAt) {

    public static PublicReviewView from(Review review) {
        return new PublicReviewView(
                review.id(),
                review.targetType().code(),
                review.targetId(),
                review.rating(),
                review.comment(),
                review.response() == null ? null : ReviewResponseView.from(review.response()),
                review.createdAt(),
                review.updatedAt());
    }
}
