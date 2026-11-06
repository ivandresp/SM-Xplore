package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.UserView;
import com.smxplore.users.application.port.in.GetUserDetailUseCase;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class GetUserDetailService implements GetUserDetailUseCase {

    private final UserRepository users;

    public GetUserDetailService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Mono<UserView> get(UUID userId) {
        return users.findById(userId)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .map(UserView::from);
    }
}
