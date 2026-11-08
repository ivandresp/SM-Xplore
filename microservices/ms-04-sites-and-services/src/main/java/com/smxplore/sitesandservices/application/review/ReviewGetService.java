package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Lectura de una resena por id. El contenido no publicado (HIDDEN, DELETED, PENDING_REVIEW)
 * solo lo ven el administrador y su autor; para el resto se responde 404 y no se filtra su
 * existencia.
 */
@RequiredArgsConstructor
public class ReviewGetService implements GetReviewUseCase {

    private final CatalogLookup lookup;

    @Override
    public Mono<Review> handle(UUID reviewId, Actor actor) {
        return lookup.review(reviewId)
                .flatMap(review -> canRead(review, actor)
                        ? Mono.just(review)
                        : Mono.error(new ResourceNotFoundException("Review", reviewId)));
    }

    private static boolean canRead(Review review, Actor actor) {
        if (review.status() == ReviewStatus.PUBLISHED) {
            return true;
        }
        return actor != null && (actor.isAdmin() || actor.owns(review.touristId()));
    }
}
