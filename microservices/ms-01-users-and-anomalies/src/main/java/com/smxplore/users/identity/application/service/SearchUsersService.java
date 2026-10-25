package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.dto.UserSearchCriteria;
import com.smxplore.users.identity.application.dto.UserSummaryView;
import com.smxplore.users.identity.application.port.in.SearchUsersUseCase;
import com.smxplore.users.identity.application.port.out.UserRepository;

public final class SearchUsersService implements SearchUsersUseCase {

    private final UserRepository users;

    public SearchUsersService(UserRepository users) {
        this.users = users;
    }

    @Override
    public PageResult<UserSummaryView> search(UserSearchCriteria criteria) {
        return users.search(criteria);
    }
}
