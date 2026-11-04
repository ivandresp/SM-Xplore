package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.DisableMfaUseCase;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.exception.MfaNotEnabledException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.MfaEnrollment;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class DisableMfaService implements DisableMfaUseCase {

    private final UserRepository users;
    private final MfaRepository mfa;
    private final PasswordHasher passwordHasher;
    private final MfaCodeVerifier codeVerifier;
    private final TransactionPort transactions;
    private final AuditTrailRecorder audit;
    private final Clock clock;

    public DisableMfaService(
            UserRepository users,
            MfaRepository mfa,
            PasswordHasher passwordHasher,
            MfaCodeVerifier codeVerifier,
            TransactionPort transactions,
            AuditTrailRecorder audit,
            Clock clock) {
        this.users = users;
        this.mfa = mfa;
        this.passwordHasher = passwordHasher;
        this.codeVerifier = codeVerifier;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Mono<Void> disable(UUID userId, String password, String code) {
        return users.findById(userId)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .flatMap(user -> passwordHasher
                        .matches(password, user.passwordHash())
                        .flatMap(matches -> {
                            if (!matches) {
                                return Mono.error(new InvalidCurrentPasswordException());
                            }
                            Instant now = clock.instant();
                            return mfa.findByUserId(userId)
                                    .filter(MfaEnrollment::enabled)
                                    .switchIfEmpty(Mono.error(new MfaNotEnabledException()))
                                    .flatMap(enrollment -> codeVerifier
                                            .verify(userId, enrollment, code, now)
                                            .flatMap(valid -> Boolean.TRUE.equals(valid)
                                                    ? transactions.execute(mfa.deleteByUserId(userId)
                                                            .then(audit.record(
                                                                    userId,
                                                                    AuditAction.MFA_DESACTIVADO,
                                                                    userId,
                                                                    Map.of("metodo", "TOTP"),
                                                                    now)))
                                                    : Mono.error(new InvalidMfaCodeException())));
                        }));
    }
}
