package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.RespondReviewCommand;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.RespondReviewUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.ReviewNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class RespondReviewService implements RespondReviewUseCase {

    private final ReviewRepository reviews;
    private final Clock clock;

    public RespondReviewService(ReviewRepository reviews, Clock clock) {
        this.reviews = reviews;
        this.clock = clock;
    }

    @Override
    public Mono<ReviewView> respond(UUID responderId, String reviewId, RespondReviewCommand command) {
        if (command == null || command.text() == null || command.text().isBlank()) {
            return Mono.error(new ValidationException("La respuesta es obligatoria"));
        }

        return reviews.findById(reviewId)
                .switchIfEmpty(Mono.error(ReviewNotFoundException::new))
                .flatMap(review -> {
                    if (!review.targetId().equals(responderId)) {
                        return Mono.error(
                                new ForbiddenOperationException("Solo el destino de la reseña puede responderla"));
                    }
                    review.respond(responderId, command.text(), clock.instant());
                    return reviews.save(review);
                })
                .map(ReviewView::from);
    }
}
