package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.GetCurrentUserUseCase;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.UserId;

public final class GetCurrentUserService implements GetCurrentUserUseCase {

    private final UserRepository users;

    public GetCurrentUserService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserView get(UserId userId) {
        return users.findById(userId).map(UserView::from).orElseThrow(UserNotFoundException::new);
    }
}
