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
 * Listado de resenas. Un administrador ve cualquier estado y cualquier turista. El resto:
 * - solo ve PUBLISHED, salvo el propio turista consultando su historial (touristId propio),
 *   que puede ver el estado real de sus resenas (incluidas HIDDEN/DELETED);
 * - solo puede filtrar por su propio touristId, salvo la gestora/prestadora, que puede
 *   filtrar resenas publicadas por turista dentro de su catalogo.
 */
@RequiredArgsConstructor
public class ReviewListService implements ListReviewsUseCase {

    private final ReviewRepositoryPort reviewRepository;

    @Override
    public Mono<PageResult<Review>> handle(ReviewFilter filter, Actor actor) {
        boolean ownsHistory = filter.touristId() != null && actor.owns(filter.touristId());
        if (!actor.isAdmin()) {
            boolean staff = actor.isGestora() || actor.isPrestadora();
            if (filter.status() != null && filter.status() != ReviewStatus.PUBLISHED && !ownsHistory) {
                return Mono.error(new ForbiddenOperationException(
                        "Only administrators can list reviews with status " + filter.status()));
            }
            if (filter.touristId() != null && !ownsHistory && !staff) {
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
