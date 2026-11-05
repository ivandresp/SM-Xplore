package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Listado publico de resenas. Solo un administrador puede pedir estados distintos de
 * PUBLISHED o el historial de otro turista; el resto de usuarios ve exclusivamente el
 * contenido publicado (y, si filtra por turista, el suyo).
 */
@RequiredArgsConstructor
public class ReviewListService implements ListReviewsUseCase {

    private final ReviewRepositoryPort reviewRepository;

    @Override
    public Mono<PageResult<Review>> handle(ReviewFilter filter, Actor actor) {
        if (!actor.isAdmin()) {
            if (filter.status() != null && filter.status() != ReviewStatus.PUBLISHED) {
                return Mono.error(new ForbiddenOperationException(
                        "Only administrators can list reviews with status " + filter.status()));
            }
            if (filter.touristId() != null && !actor.owns(filter.touristId())) {
                return Mono.error(new ForbiddenOperationException(
                        "Only administrators can list another tourist's reviews"));
            }
        }
        ReviewStatus status = filter.status() == null ? ReviewStatus.PUBLISHED : filter.status();
        return reviewRepository.findAll(new ReviewFilter(
                filter.itemType(),
                filter.itemId(),
                filter.touristId(),
                status,
                filter.page(),
                filter.size()));
    }
}
