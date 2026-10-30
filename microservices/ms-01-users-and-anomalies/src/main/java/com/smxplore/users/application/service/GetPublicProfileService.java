package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PublicProfileView;
import com.smxplore.users.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Role;
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
                .filter(user -> user.isActive() && isPublicRole(user))
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .map(PublicProfileView::from);
    }

    private static boolean isPublicRole(Usuario user) {
        if (user.role() == Role.GUIDE) {
            return true;
        }
        return user instanceof PrestadoraServicios provider
                && provider.registrationStatus() == ProviderRegistrationStatus.APROBADO;
    }
}
