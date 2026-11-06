package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.BootstrapAdminCommand;
import reactor.core.publisher.Mono;

public interface BootstrapAdminUseCase {

    Mono<Void> bootstrap(BootstrapAdminCommand command);
}
