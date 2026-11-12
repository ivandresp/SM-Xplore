package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RegistrationPage;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import reactor.core.publisher.Mono;

public interface ListRegistrationsUseCase {

    Mono<RegistrationPage> search(RegistrationSearchCriteria criteria);
}
