package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PublicProfileView;
import com.smxplore.users.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.RegistrationReviewable;
import com.smxplore.users.domain.model.RegistrationStatus;
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
                .filter(user -> user.isActive() && isPublicProfile(user))
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .map(PublicProfileView::from);
    }

    /**
     * Solo se publica un perfil cuyo alta está aprobada.
     *
     * <p>Guías y prestadoras comparten la misma regla: un alta pendiente o rechazada no existe para el catálogo
     * público. Antes bastaba con tener el rol {@code GUIDE}, así que un guía pendiente o rechazado exponía su nombre
     * y su licencia.
     */
    private static boolean isPublicProfile(Usuario user) {
        return user instanceof RegistrationReviewable reviewable
                && reviewable.registrationStatus() == RegistrationStatus.APROBADO;
    }
}
