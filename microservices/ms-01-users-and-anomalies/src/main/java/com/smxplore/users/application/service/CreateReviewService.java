package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.CreateReviewCommand;
import com.smxplore.users.application.dto.ReviewAiRequest;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.application.port.in.CreateReviewUseCase;
import com.smxplore.users.application.port.out.ReviewAiFilter;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.DuplicateReviewException;
import com.smxplore.users.domain.exception.ReviewFilterUnavailableException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Review;
import com.smxplore.users.domain.model.ReviewTargetType;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.ReviewAiPolicy;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class CreateReviewService implements CreateReviewUseCase {

    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ReviewAiFilter aiFilter;
    private final ReviewAiPolicy aiPolicy;
    private final Clock clock;

    public CreateReviewService(
            ReviewRepository reviews,
            UserRepository users,
            ReviewAiFilter aiFilter,
            ReviewAiPolicy aiPolicy,
            Clock clock) {
        this.reviews = reviews;
        this.users = users;
        this.aiFilter = aiFilter;
        this.aiPolicy = aiPolicy;
        this.clock = clock;
    }

    @Override
    public Mono<ReviewView> create(UUID touristId, CreateReviewCommand command) {
        if (command.targetType() == null || command.targetId() == null) {
            return Mono.error(new ValidationException("El destino de la reseña es obligatorio"));
        }

        return users.findById(command.targetId())
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .flatMap(target -> createForTarget(touristId, target, command));
    }

    private Mono<ReviewView> createForTarget(UUID touristId, Usuario target, CreateReviewCommand command) {
        validateTarget(target, command.targetType());

        return reviews.existsByTouristAndTarget(touristId, command.targetType(), command.targetId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateReviewException());
                    }
                    Review review = Review.create(
                            touristId,
                            command.targetType(),
                            command.targetId(),
                            command.rating(),
                            command.comment(),
                            clock.instant());
                    return saveWithAiAssessment(review);
                });
    }

    private Mono<ReviewView> saveWithAiAssessment(Review review) {
        if (review.comment() == null) {
            return reviews.save(review).map(ReviewView::from);
        }

        ReviewAiRequest request = new ReviewAiRequest(review.targetType().code(), review.rating(), review.comment());
        return aiFilter.assess(request).flatMap(assessment -> {
            if (aiPolicy.shouldRejectOnFailure(assessment)) {
                return Mono.error(new ReviewFilterUnavailableException());
            }
            review.applyAiAssessment(assessment, aiPolicy, clock.instant());
            return reviews.save(review).map(ReviewView::from);
        });
    }

    private static void validateTarget(Usuario target, ReviewTargetType targetType) {
        if (!target.isActive()) {
            throw new ValidationException("El destino de la reseña no está disponible");
        }
        if (targetType == ReviewTargetType.GUIDE) {
            if (target.role() != Role.GUIDE) {
                throw new ValidationException("El destino indicado no es un guía turístico");
            }
            return;
        }
        if (!(target instanceof PrestadoraServicios provider)
                || target.role() != Role.SERVICE_PROVIDER
                || provider.registrationStatus() != ProviderRegistrationStatus.APROBADO) {
            throw new ValidationException("El destino indicado no es una prestadora aprobada");
        }
    }
}
