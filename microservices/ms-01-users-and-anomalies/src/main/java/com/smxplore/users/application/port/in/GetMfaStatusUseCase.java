package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.MfaStatusView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface GetMfaStatusUseCase {

    Mono<MfaStatusView> status(UUID userId);
}
