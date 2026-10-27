package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.MfaEnrollmentView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface EnrollMfaUseCase {

    Mono<MfaEnrollmentView> enroll(UUID userId);
}
