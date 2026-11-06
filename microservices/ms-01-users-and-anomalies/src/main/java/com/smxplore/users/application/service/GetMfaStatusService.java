package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.MfaStatusView;
import com.smxplore.users.application.port.in.GetMfaStatusUseCase;
import com.smxplore.users.application.port.out.MfaRepository;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class GetMfaStatusService implements GetMfaStatusUseCase {

    private final MfaRepository mfa;

    public GetMfaStatusService(MfaRepository mfa) {
        this.mfa = mfa;
    }

    @Override
    public Mono<MfaStatusView> status(UUID userId) {
        return mfa.findByUserId(userId)
                .map(enrollment -> new MfaStatusView(enrollment.enabled(), enrollment.isPending()))
                .defaultIfEmpty(new MfaStatusView(false, false));
    }
}
