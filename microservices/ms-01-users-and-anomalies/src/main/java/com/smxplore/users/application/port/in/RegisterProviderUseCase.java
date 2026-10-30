package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RegisterProviderCommand;
import com.smxplore.users.application.dto.UserView;
import reactor.core.publisher.Mono;

public interface RegisterProviderUseCase {

    Mono<UserView> register(RegisterProviderCommand command);
}
