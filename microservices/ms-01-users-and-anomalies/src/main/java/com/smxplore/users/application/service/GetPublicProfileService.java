package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PublicProfileView;
import com.smxplore.users.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.RegistrationReviewable;
import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class GetPublicProfileService implements GetPublicProfileUseCase {

    private final UserRepository users;

    public GetPublicProfileService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Mono<PublicProfileView> get(UUID userId) {
        return users.findById(userId)
                .filter(GetPublicProfileService::isPublic)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .map(PublicProfileView::from);
    }

    /**
     * Solo se publica un perfil activo cuyo alta está aprobada. Guías y prestadoras comparten la regla
     * ({@link RegistrationReviewable#isApproved}); antes bastaba con tener el rol {@code GUIDE}.
     */
    private static boolean isPublic(Usuario user) {
        return user.isActive() && RegistrationReviewable.isApproved(user);
    }
}
