package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.ConfirmMfaUseCase;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import com.smxplore.users.application.port.out.MfaEnabledNotifier;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.TotpAuthenticator;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.exception.MfaNotEnabledException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ConfirmMfaService implements ConfirmMfaUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final MfaRepository mfa;
    private final TotpAuthenticator totp;
    private final TransactionPort transactions;
    private final AuditTrailRecorder audit;
    private final MfaEnabledNotifier notifier;
    private final AuthVersionEvictor evictor;
    private final Clock clock;

    public ConfirmMfaService(
            UserRepository users,
            PasswordHasher passwordHasher,
            MfaRepository mfa,
            TotpAuthenticator totp,
            TransactionPort transactions,
            AuditTrailRecorder audit,
            MfaEnabledNotifier notifier,
            AuthVersionEvictor evictor,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.mfa = mfa;
        this.totp = totp;
        this.transactions = transactions;
        this.audit = audit;
        this.notifier = notifier;
        this.evictor = evictor;
        this.clock = clock;
    }

    @Override
    public Mono<Void> confirm(UUID userId, String code, String password) {
        return users.findById(userId)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .flatMap(user -> passwordHasher
                        .matches(password, user.passwordHash())
                        .flatMap(matches -> Boolean.TRUE.equals(matches)
                                ? activate(user, code)
                                : Mono.<Void>error(new InvalidCurrentPasswordException())));
    }

    private Mono<Void> activate(Usuario user, String code) {
        Instant now = clock.instant();
        return mfa.findByUserId(user.id())
                .filter(MfaEnrollment::isPending)
                .switchIfEmpty(
                        Mono.error(new MfaNotEnabledException("No hay una inscripción MFA pendiente de confirmar")))
                .flatMap(enrollment -> {
                    long step = totp.verify(enrollment.secret(), MfaCodeVerifier.normalize(code), now);
                    if (step < 0) {
                        return Mono.error(new InvalidMfaCodeException());
                    }
                    return transactions.execute(mfa.claimStep(user.id(), step)
                            .flatMap(claimed -> Boolean.TRUE.equals(claimed)
                                    ? activateStep(user, enrollment, now)
                                    : Mono.error(new InvalidMfaCodeException())));
                })
                .then(evictor.evict(user.id()))
                .then(Mono.defer(() -> notifier.notify(user)));
    }

    private Mono<Void> activateStep(Usuario user, MfaEnrollment enrollment, Instant now) {
        user.revokeSessions(now);
        return mfa.save(enrollment.confirm(now))
                .then(users.save(user))
                .then(audit.record(
                        user.id(),
                        AuditAction.MFA_ACTIVADO,
                        user.id(),
                        Map.of("metodo", "TOTP", "sesionesRevocadas", "true"),
                        now));
    }
}
