package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ModerateReviewCommand;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.ModerateReviewUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ReviewNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.ModerationDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ModerateReviewService implements ModerateReviewUseCase {

    private final ReviewRepository reviews;
    private final AuditTrailRecorder audit;
    private final Clock clock;

    public ModerateReviewService(ReviewRepository reviews, AuditTrailRecorder audit, Clock clock) {
        this.reviews = reviews;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Mono<ReviewView> moderate(UUID adminId, String reviewId, ModerateReviewCommand command) {
        if (command == null || command.decision() == null) {
            return Mono.error(new ValidationException("La decisión de moderación es obligatoria"));
        }

        return reviews.findById(reviewId)
                .switchIfEmpty(Mono.error(ReviewNotFoundException::new))
                .flatMap(review -> {
                    Instant now = clock.instant();
                    ModerationDecision decision = command.decision();
                    review.moderate(decision, adminId, command.reason(), now);
                    return reviews.save(review)
                            .then(audit.record(
                                    adminId,
                                    AuditAction.RESENA_MODERADA,
                                    null,
                                    details(review.id(), decision, command.reason()),
                                    now))
                            .thenReturn(review);
                })
                .map(ReviewView::from);
    }

    private static Map<String, String> details(String reviewId, ModerationDecision decision, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("resenaId", reviewId);
        details.put("decision", decision.name());
        if (reason != null && !reason.isBlank()) {
            details.put("motivo", reason.trim());
        }
        return details;
    }
}
