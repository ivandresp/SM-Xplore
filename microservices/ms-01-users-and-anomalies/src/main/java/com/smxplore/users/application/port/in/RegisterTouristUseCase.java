package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RegisterTouristCommand;
import com.smxplore.users.application.dto.UserView;
import reactor.core.publisher.Mono;

public interface RegisterTouristUseCase {

    Mono<UserView> register(RegisterTouristCommand command);
}
