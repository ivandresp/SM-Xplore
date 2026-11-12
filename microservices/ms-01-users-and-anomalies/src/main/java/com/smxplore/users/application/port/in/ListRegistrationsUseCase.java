package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import com.smxplore.users.application.dto.RegistrationSummaryView;
import reactor.core.publisher.Mono;

public interface ListRegistrationsUseCase {

    Mono<PageResult<RegistrationSummaryView>> search(RegistrationSearchCriteria criteria);
}
