package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ForgotPasswordCommand;
import reactor.core.publisher.Mono;

public interface ForgotPasswordUseCase {

    Mono<Void> handle(ForgotPasswordCommand command);
}
