package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ChangePasswordCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ChangeOwnPasswordUseCase {

    Mono<Void> change(UUID userId, ChangePasswordCommand command);
}
