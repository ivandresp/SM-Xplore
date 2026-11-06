package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.UserContactView;
import com.smxplore.users.application.port.in.GetUserContactUseCase;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Implementación del contacto interno: lee la cuenta y la proyecta a {@link UserContactView}. */
public final class GetUserContactService implements GetUserContactUseCase {

    private final UserRepository users;

    public GetUserContactService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Mono<UserContactView> get(UUID userId) {
        return users.findById(userId)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .map(UserContactView::from);
    }
}
