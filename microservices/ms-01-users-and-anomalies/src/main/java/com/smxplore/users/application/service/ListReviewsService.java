package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.PublicReviewView;
import com.smxplore.users.application.port.in.ListReviewsUseCase;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationReviewable;
import com.smxplore.users.domain.model.ReviewTargetType;
import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ListReviewsService implements ListReviewsUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviews;
    private final UserRepository users;

    public ListReviewsService(ReviewRepository reviews, UserRepository users) {
        this.reviews = reviews;
        this.users = users;
    }

    @Override
    public Mono<PageResult<PublicReviewView>> list(ReviewTargetType targetType, UUID targetId, int page, int size) {
        if (targetType == null || targetId == null) {
            return Mono.error(new ValidationException("El destino de la reseña es obligatorio"));
        }
        if (page < 0) {
            return Mono.error(new ValidationException("La página no puede ser negativa"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            return Mono.error(new ValidationException("El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE));
        }

        // MS01-B8: el feed de reseñas es superficie pública, así que un alta sin aprobar responde igual que su
        // perfil: 404. Antes bastaba con que existiera el identificador.
        return requirePublicTarget(targetId)
                .then(reviews.findVisible(targetType, targetId, page, size))
                .map(result -> PageResult.of(
                        result.content().stream().map(PublicReviewView::from).toList(),
                        result.page(),
                        result.size(),
                        result.totalElements()));
    }

    private Mono<Usuario> requirePublicTarget(UUID targetId) {
        return users.findById(targetId)
                .filter(user -> user.isActive() && RegistrationReviewable.isApproved(user))
                .switchIfEmpty(Mono.error(UserNotFoundException::new));
    }
}
