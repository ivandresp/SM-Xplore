package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RegisterGestoraCommand;
import com.smxplore.users.application.dto.UserView;
import reactor.core.publisher.Mono;

public interface RegisterGestoraUseCase {

    Mono<UserView> register(RegisterGestoraCommand command);
}
