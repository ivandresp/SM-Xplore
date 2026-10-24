package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.PublicProfileView;
import com.smxplore.users.identity.application.port.in.GetPublicProfileUseCase;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
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
        if (user.role() != Role.GUIA && user.role() != Role.PRESTADORA_SERVICIOS) {
            throw new UserNotFoundException();
        }
        return PublicProfileView.from(user);
    }
}
