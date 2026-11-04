package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReputationSummaryView;
import com.smxplore.users.application.port.in.GetReputationSummaryUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class GetReputationSummaryService implements GetReputationSummaryUseCase {

    private final ReviewRepository reviews;

    public GetReputationSummaryService(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public Mono<ReputationSummaryView> get(ReviewTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return Mono.error(new ValidationException("El destino de la reseña es obligatorio"));
        }
        return reviews.summary(targetType, targetId)
                .map(summary -> ReputationSummaryView.from(targetType, targetId, summary));
    }
}
