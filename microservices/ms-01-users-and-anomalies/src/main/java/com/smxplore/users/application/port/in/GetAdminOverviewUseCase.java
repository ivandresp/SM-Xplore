package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AdminOverviewView;
import reactor.core.publisher.Mono;

public interface GetAdminOverviewUseCase {

    Mono<AdminOverviewView> get();
}
