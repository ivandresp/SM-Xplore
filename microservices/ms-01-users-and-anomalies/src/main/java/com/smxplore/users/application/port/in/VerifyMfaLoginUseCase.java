package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.LoginResult;
import com.smxplore.users.application.dto.VerifyMfaCommand;
import reactor.core.publisher.Mono;

public interface VerifyMfaLoginUseCase {

    Mono<LoginResult> verify(VerifyMfaCommand command);
}
