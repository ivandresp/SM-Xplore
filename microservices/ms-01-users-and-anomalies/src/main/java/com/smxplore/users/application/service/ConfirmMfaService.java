package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.ConfirmMfaUseCase;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.TotpAuthenticator;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.exception.MfaNotEnabledException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.MfaEnrollment;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ConfirmMfaService implements ConfirmMfaUseCase {

    private final MfaRepository mfa;
    private final TotpAuthenticator totp;
    private final TransactionPort transactions;
    private final AuditTrailRecorder audit;
    private final Clock clock;

    public ConfirmMfaService(
            MfaRepository mfa,
            TotpAuthenticator totp,
            TransactionPort transactions,
            AuditTrailRecorder audit,
            Clock clock) {
        this.mfa = mfa;
        this.totp = totp;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Mono<Void> confirm(UUID userId, String code) {
        return mfa.findByUserId(userId)
                .filter(MfaEnrollment::isPending)
                .switchIfEmpty(
                        Mono.error(new MfaNotEnabledException("No hay una inscripción MFA pendiente de confirmar")))
                .flatMap(enrollment -> {
                    Instant now = clock.instant();
                    long step = totp.verify(enrollment.secret(), MfaCodeVerifier.normalize(code), now);
                    if (step < 0) {
                        return Mono.error(new InvalidMfaCodeException());
                    }
                    return transactions.execute(mfa.claimStep(userId, step)
                            .flatMap(claimed -> Boolean.TRUE.equals(claimed)
                                    ? mfa.save(enrollment.confirm(now))
                                            .then(audit.record(
                                                    userId,
                                                    AuditAction.MFA_ACTIVADO,
                                                    userId,
                                                    Map.of("metodo", "TOTP"),
                                                    now))
                                    : Mono.error(new InvalidMfaCodeException())));
                });
    }
}
