package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.VerifyEmailUseCase;
import com.smxplore.users.application.port.out.EmailVerificationRepository;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.InvalidEmailVerificationTokenException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Verificación de correo (MS01-18).
 *
 * <p>El token es de un solo uso: se busca por hash, se exige propósito {@link TokenPurpose#EMAIL_VERIFICATION} y
 * vigencia, se marca como usado y se invalidan los demás tokens de verificación pendientes de la cuenta. La marca de
 * verificado vive en {@code email_verifications} para no tocar el modelo del agregado en esta fase.
 */
public final class VerifyEmailService implements VerifyEmailUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailVerificationRepository verifications;
    private final TokenHasher tokenHasher;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public VerifyEmailService(
            UserRepository users,
            PasswordResetTokenRepository tokens,
            EmailVerificationRepository verifications,
            TokenHasher tokenHasher,
            AuditTrailRecorder audit,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.verifications = verifications;
        this.tokenHasher = tokenHasher;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Mono.error(new InvalidEmailVerificationTokenException());
        }
        Instant now = clock.instant();
        String tokenHash = tokenHasher.hash(rawToken);

        return transactions
                .execute(Mono.defer(() -> tokens.findByTokenHash(tokenHash)
                        .filter(token -> token.purpose() == TokenPurpose.EMAIL_VERIFICATION && token.isUsable(now))
                        .switchIfEmpty(Mono.error(InvalidEmailVerificationTokenException::new))
                        .flatMap(token -> users.findById(token.userId())
                                .switchIfEmpty(Mono.error(InvalidEmailVerificationTokenException::new))
                                .flatMap(user -> verifications
                                        .markVerified(user.id(), now)
                                        .then(tokens.invalidateByPurposeForUser(
                                                user.id(), TokenPurpose.EMAIL_VERIFICATION, now))
                                        .then(tokens.save(token.markUsed(now)))
                                        .then(audit.record(
                                                user.id(), AuditAction.CORREO_VERIFICADO, user.id(), Map.of(), now))))))
                .then();
    }
}
