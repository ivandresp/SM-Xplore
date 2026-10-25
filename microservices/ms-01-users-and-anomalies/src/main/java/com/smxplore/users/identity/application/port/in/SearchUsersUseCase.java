package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.dto.UserSearchCriteria;
import com.smxplore.users.identity.application.dto.UserSummaryView;

public interface SearchUsersUseCase {

    PageResult<UserSummaryView> search(UserSearchCriteria criteria);
}
