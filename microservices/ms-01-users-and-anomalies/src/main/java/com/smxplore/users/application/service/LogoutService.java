package com.smxplore.users.application.service;

import com.smxplore.users.application.port.in.LogoutUseCase;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class LogoutService implements LogoutUseCase {

    private final UserRepository users;
    private final TransactionPort transactions;
    private final AuditTrailRecorder audit;
    private final AuthVersionEvictor evictor;
    private final Clock clock;

    public LogoutService(
            UserRepository users,
            TransactionPort transactions,
            AuditTrailRecorder audit,
            AuthVersionEvictor evictor,
            Clock clock) {
        this.users = users;
        this.transactions = transactions;
        this.audit = audit;
        this.evictor = evictor;
        this.clock = clock;
    }

    @Override
    public Mono<Void> logout(UUID userId) {
        return transactions
                .execute(Mono.defer(() -> users.findById(userId)
                        .switchIfEmpty(Mono.error(UserNotFoundException::new))
                        .flatMap(user -> revoke(user, clock.instant()))))
                .then(evictor.evict(userId));
    }

    private Mono<Usuario> revoke(Usuario user, Instant now) {
        user.revokeSessions(now);
        return users.save(user)
                .then(audit.record(user.id(), AuditAction.SESION_REVOCADA, user.id(), Map.of("alcance", "TODAS"), now))
                .thenReturn(user);
    }
}
