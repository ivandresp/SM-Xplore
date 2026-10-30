package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.RecoveryCodesView;
import com.smxplore.users.application.port.in.RegenerateRecoveryCodesUseCase;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.application.port.out.RecoveryCodeGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import reactor.core.publisher.Mono;

public final class RegenerateRecoveryCodesService implements RegenerateRecoveryCodesUseCase {

    private static final int RECOVERY_CODE_COUNT = 10;

    private final UserRepository users;
    private final MfaRepository mfa;
    private final PasswordHasher passwordHasher;
    private final RecoveryCodeGenerator recoveryCodeGenerator;
    private final TokenHasher tokenHasher;
    private final MfaCodeVerifier codeVerifier;
    private final TransactionPort transactions;
    private final AuditTrailRecorder audit;
    private final Clock clock;

    public RegenerateRecoveryCodesService(
            UserRepository users,
            MfaRepository mfa,
            PasswordHasher passwordHasher,
            RecoveryCodeGenerator recoveryCodeGenerator,
            TokenHasher tokenHasher,
            MfaCodeVerifier codeVerifier,
            TransactionPort transactions,
            AuditTrailRecorder audit,
            Clock clock) {
        this.users = users;
        this.mfa = mfa;
        this.passwordHasher = passwordHasher;
        this.recoveryCodeGenerator = recoveryCodeGenerator;
        this.tokenHasher = tokenHasher;
        this.codeVerifier = codeVerifier;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Mono<RecoveryCodesView> regenerate(UUID userId, String password, String code) {
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
                                                    ? replace(user, now)
                                                    : Mono.error(new InvalidMfaCodeException())));
                        }));
    }

    private Mono<RecoveryCodesView> replace(Usuario user, Instant now) {
        List<String> codes = IntStream.range(0, RECOVERY_CODE_COUNT)
                .mapToObj(index -> recoveryCodeGenerator.generate())
                .toList();
        List<String> hashes = codes.stream()
                .map(code -> tokenHasher.hash(MfaCodeVerifier.recoveryForm(code)))
                .toList();
        return transactions
                .execute(mfa.replaceRecoveryCodes(user.id(), hashes, now)
                        .then(audit.record(
                                user.id(),
                                AuditAction.CODIGOS_RECUPERACION_REGENERADOS,
                                user.id(),
                                Map.of("cantidad", String.valueOf(RECOVERY_CODE_COUNT)),
                                now)))
                .thenReturn(new RecoveryCodesView(codes));
    }
}
