package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ReputationSummaryView;
import com.smxplore.users.application.port.in.GetReputationSummaryUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationReviewable;
import com.smxplore.users.domain.model.ReviewTargetType;
import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class GetReputationSummaryService implements GetReputationSummaryUseCase {

    private final ReviewRepository reviews;
    private final UserRepository users;

    public GetReputationSummaryService(ReviewRepository reviews, UserRepository users) {
        this.reviews = reviews;
        this.users = users;
    }

    @Override
    public Mono<ReputationSummaryView> get(ReviewTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return Mono.error(new ValidationException("El destino de la reseña es obligatorio"));
        }
        // MS01-B8: la reputación también es pública; se sirve con la misma regla que el perfil y el feed.
        return requirePublicTarget(targetId)
                .then(reviews.summary(targetType, targetId))
                .map(summary -> ReputationSummaryView.from(targetType, targetId, summary));
    }

    private Mono<Usuario> requirePublicTarget(UUID targetId) {
        return users.findById(targetId)
                .filter(user -> user.isActive() && RegistrationReviewable.isApproved(user))
                .switchIfEmpty(Mono.error(UserNotFoundException::new));
    }
}
