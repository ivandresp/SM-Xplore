package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReportReviewCommand;
import com.smxplore.users.application.port.in.ReportReviewUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ReviewNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ReportReviewService implements ReportReviewUseCase {

    private final ReviewRepository reviews;
    private final Clock clock;

    public ReportReviewService(ReviewRepository reviews, Clock clock) {
        this.reviews = reviews;
        this.clock = clock;
    }

    @Override
    public Mono<Void> report(UUID reporterId, String reviewId, ReportReviewCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            return Mono.error(new ValidationException("El motivo del reporte es obligatorio"));
        }

        return reviews.findById(reviewId)
                .switchIfEmpty(Mono.error(ReviewNotFoundException::new))
                .flatMap(review -> {
                    review.report(reporterId, command.reason(), clock.instant());
                    return reviews.save(review);
                })
                .then();
    }
}
