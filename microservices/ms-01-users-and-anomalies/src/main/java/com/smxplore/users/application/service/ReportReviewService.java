package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReportReviewCommand;
import com.smxplore.users.application.port.in.ReportReviewUseCase;
import com.smxplore.users.application.port.out.ReviewReportQuota;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ReviewNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ReportReviewService implements ReportReviewUseCase {

    private final ReviewRepository reviews;
    private final ReviewReportQuota quota;
    private final Clock clock;

    public ReportReviewService(ReviewRepository reviews, ReviewReportQuota quota, Clock clock) {
        this.reviews = reviews;
        this.quota = quota;
        this.clock = clock;
    }

    /**
     * MS01-22: además de impedir reportes duplicados sobre la misma reseña, se aplica una cuota por turista y ventana
     * para que un atacante no pueda inundar el buzón de moderación (y desplazar los reportes legítimos).
     *
     * <p>La cuota se consume solo cuando el motivo es válido; un reporte sobre una reseña inexistente sí consume
     * cuota porque la intención de reportar ya existía.
     */
    @Override
    public Mono<Void> report(UUID reporterId, String reviewId, ReportReviewCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            return Mono.error(new ValidationException("El motivo del reporte es obligatorio"));
        }

        return quota.checkAndRecord(reporterId)
                .then(Mono.defer(() -> reviews.findById(reviewId)))
                .switchIfEmpty(Mono.error(ReviewNotFoundException::new))
                .flatMap(review -> {
                    review.report(reporterId, command.reason(), clock.instant());
                    return reviews.save(review);
                })
                .then();
    }
}
