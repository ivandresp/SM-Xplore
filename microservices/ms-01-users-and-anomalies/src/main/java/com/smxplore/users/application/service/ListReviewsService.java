package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.ListReviewsUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ListReviewsService implements ListReviewsUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviews;

    public ListReviewsService(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public Mono<PageResult<ReviewView>> list(ReviewTargetType targetType, UUID targetId, int page, int size) {
        if (targetType == null || targetId == null) {
            return Mono.error(new ValidationException("El destino de la reseña es obligatorio"));
        }
        if (page < 0) {
            return Mono.error(new ValidationException("La página no puede ser negativa"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            return Mono.error(new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE));
        }

        return reviews.findVisible(targetType, targetId, page, size)
                .map(result -> PageResult.of(
                        result.content().stream().map(ReviewView::from).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements()));
    }
}
