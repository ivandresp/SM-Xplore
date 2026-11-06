package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.dto.LoginResult;
import reactor.core.publisher.Mono;

public interface LoginUseCase {

    Mono<LoginResult> login(LoginCommand command);
}
