package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.application.port.in.GetUserDetailUseCase;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.exception.UserNotFoundException;
import com.smxplore.users.identity.domain.model.UserId;

public final class GetUserDetailService implements GetUserDetailUseCase {

    private final UserRepository users;

    public GetUserDetailService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserView get(UserId userId) {
        return UserView.from(users.findById(userId).orElseThrow(UserNotFoundException::new));
    }
}
