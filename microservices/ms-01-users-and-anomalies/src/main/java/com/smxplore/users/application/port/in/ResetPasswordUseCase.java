package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ResetPasswordCommand;
import reactor.core.publisher.Mono;

public interface ResetPasswordUseCase {

    Mono<Void> reset(ResetPasswordCommand command);
}
