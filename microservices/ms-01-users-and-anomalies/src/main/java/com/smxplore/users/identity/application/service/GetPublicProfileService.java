package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.PublicProfileView;
import com.smxplore.users.identity.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.PrestadoraServicios;
import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;

public final class GetPublicProfileService implements GetPublicProfileUseCase {

    private final UserRepository users;

    public GetPublicProfileService(UserRepository users) {
        this.users = users;
    }

    @Override
    public PublicProfileView get(UserId userId) {
        Usuario user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!user.isActive() || !isPublicRole(user)) {
            throw new UserNotFoundException();
        }
        return PublicProfileView.from(user);
    }

    private static boolean isPublicRole(Usuario user) {
        if (user.role() == Role.GUIA) {
            return true;
        }
        return user instanceof PrestadoraServicios provider
                && provider.registrationStatus() == ProviderRegistrationStatus.APROBADO;
    }
}
