package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AiFlaggedReviewView;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.in.ListAiFlaggedReviewsUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ValidationException;
import reactor.core.publisher.Mono;

public final class ListAiFlaggedReviewsService implements ListAiFlaggedReviewsUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviews;

    public ListAiFlaggedReviewsService(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public Mono<PageResult<AiFlaggedReviewView>> list(int page, int size) {
        if (page < 0) {
            return Mono.error(new ValidationException("La página no puede ser negativa"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            return Mono.error(new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE));
        }

        return reviews.findAiFlagged(page, size)
                .map(result -> PageResult.of(
                        result.content().stream().map(AiFlaggedReviewView::from).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements()));
    }
}
