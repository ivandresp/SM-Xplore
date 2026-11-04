package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserSummaryView;
import reactor.core.publisher.Mono;

public interface SearchUsersUseCase {

    Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria);
}
