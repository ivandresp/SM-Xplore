package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import com.smxplore.users.application.dto.RegistrationSummaryView;
import com.smxplore.users.application.port.in.ListRegistrationsUseCase;
import com.smxplore.users.application.port.out.RegistrationRepository;
import reactor.core.publisher.Mono;

public final class ListRegistrationsService implements ListRegistrationsUseCase {

    private final RegistrationRepository registrations;

    public ListRegistrationsService(RegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Override
    public Mono<PageResult<RegistrationSummaryView>> search(RegistrationSearchCriteria criteria) {
        return registrations.search(criteria);
    }
}
