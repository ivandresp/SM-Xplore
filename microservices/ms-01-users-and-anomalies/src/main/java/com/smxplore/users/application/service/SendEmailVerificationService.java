package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.EmailVerificationPolicy;
import com.smxplore.users.application.port.in.SendEmailVerificationUseCase;
import com.smxplore.users.application.port.out.EmailVerificationNotifier;
import com.smxplore.users.application.port.out.EmailVerificationRepository;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.application.port.out.TokenGenerator;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Reenvío del enlace de verificación (MS01-18).
 *
 * <p>Cada reenvío invalida los tokens de verificación pendientes para no dejar varios enlaces válidos vivos. Si la
 * cuenta ya está verificada o la verificación está desactivada no se hace nada.
 */
public final class SendEmailVerificationService implements SendEmailVerificationUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailVerificationRepository verifications;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final EmailVerificationNotifier notifier;
    private final EmailVerificationPolicy properties;
    private final TransactionPort transactions;
    private final Clock clock;

    public SendEmailVerificationService(
            UserRepository users,
            PasswordResetTokenRepository tokens,
            EmailVerificationRepository verifications,
            TokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            EmailVerificationNotifier notifier,
            EmailVerificationPolicy properties,
            TransactionPort transactions,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.verifications = verifications;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.notifier = notifier;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<Void> send(UUID userId) {
        if (!properties.enabled()) {
            return Mono.empty();
        }
        Instant now = clock.instant();
        String rawToken = tokenGenerator.generate();

        return users.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException()))
                .flatMap(user -> verifications
                        .isVerified(user.id())
                        .flatMap(verified -> verified ? Mono.empty() : issueAndNotify(user, rawToken, now)));
    }

    private Mono<Void> issueAndNotify(Usuario user, String rawToken, Instant now) {
        PasswordResetToken token = PasswordResetToken.create(
                user.id(), tokenHasher.hash(rawToken), now, properties.tokenTtl(), TokenPurpose.EMAIL_VERIFICATION);

        return transactions
                .execute(tokens.invalidateByPurposeForUser(user.id(), TokenPurpose.EMAIL_VERIFICATION, now)
                        .then(tokens.save(token))
                        .then())
                .then(notifier.notify(user, rawToken));
    }
}
