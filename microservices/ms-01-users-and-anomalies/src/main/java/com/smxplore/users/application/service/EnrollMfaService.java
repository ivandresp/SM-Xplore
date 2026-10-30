package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.MfaEnrollmentView;
import com.smxplore.users.application.port.in.EnrollMfaUseCase;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.RecoveryCodeGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TotpAuthenticator;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.MfaAlreadyEnabledException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import reactor.core.publisher.Mono;

public final class EnrollMfaService implements EnrollMfaUseCase {

    private static final int RECOVERY_CODE_COUNT = 10;

    private final UserRepository users;
    private final MfaRepository mfa;
    private final TotpAuthenticator totp;
    private final RecoveryCodeGenerator recoveryCodeGenerator;
    private final TokenHasher tokenHasher;
    private final TransactionPort transactions;
    private final Clock clock;
    private final String issuerName;

    public EnrollMfaService(
            UserRepository users,
            MfaRepository mfa,
            TotpAuthenticator totp,
            RecoveryCodeGenerator recoveryCodeGenerator,
            TokenHasher tokenHasher,
            TransactionPort transactions,
            Clock clock,
            String issuerName) {
        this.users = users;
        this.mfa = mfa;
        this.totp = totp;
        this.recoveryCodeGenerator = recoveryCodeGenerator;
        this.tokenHasher = tokenHasher;
        this.transactions = transactions;
        this.clock = clock;
        this.issuerName = issuerName;
    }

    @Override
    public Mono<MfaEnrollmentView> enroll(UUID userId) {
        return users.findById(userId)
                .switchIfEmpty(Mono.error(UserNotFoundException::new))
                .flatMap(user -> mfa.findByUserId(userId)
                        .filter(MfaEnrollment::enabled)
                        .flatMap(existing -> Mono.<MfaEnrollmentView>error(new MfaAlreadyEnabledException()))
                        .switchIfEmpty(Mono.defer(() -> create(user))));
    }

    private Mono<MfaEnrollmentView> create(Usuario user) {
        Instant now = clock.instant();
        String secret = totp.generateSecret();
        List<String> recoveryCodes = IntStream.range(0, RECOVERY_CODE_COUNT)
                .mapToObj(index -> recoveryCodeGenerator.generate())
                .toList();
        List<String> hashes = recoveryCodes.stream()
                .map(code -> tokenHasher.hash(MfaCodeVerifier.recoveryForm(code)))
                .toList();
        MfaEnrollment enrollment = MfaEnrollment.pending(user.id(), secret, now);

        return transactions
                .execute(mfa.save(enrollment).then(mfa.replaceRecoveryCodes(user.id(), hashes, now)))
                .thenReturn(new MfaEnrollmentView(
                        secret, totp.provisioningUri(issuerName, user.email().value(), secret), recoveryCodes));
    }
}
