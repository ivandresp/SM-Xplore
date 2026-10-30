package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.application.port.in.SearchUsersUseCase;
import com.smxplore.users.application.port.out.UserRepository;
import reactor.core.publisher.Mono;

public final class SearchUsersService implements SearchUsersUseCase {

    private final UserRepository users;

    public SearchUsersService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria) {
        return users.search(criteria);
    }
}
