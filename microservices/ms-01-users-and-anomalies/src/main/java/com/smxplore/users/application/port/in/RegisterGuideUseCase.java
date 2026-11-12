package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RegisterGuideCommand;
import com.smxplore.users.application.dto.UserView;
import reactor.core.publisher.Mono;

public interface RegisterGuideUseCase {

    Mono<UserView> register(RegisterGuideCommand command);
}
