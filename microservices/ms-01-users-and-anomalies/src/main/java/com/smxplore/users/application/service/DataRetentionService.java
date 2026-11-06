package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.RetentionOutcome;
import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.application.port.out.LoginAttemptRepository;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

/**
 * Aplica la política de retención: elimina artefactos de autenticación vencidos y entradas de auditoría fuera del
 * periodo legal configurado. La anonimización de cuentas ocurre al darlas de baja, no aquí.
 */
public final class DataRetentionService {

    private final PasswordResetTokenRepository resetTokens;
    private final LoginAttemptRepository loginAttempts;
    private final MfaRepository mfa;
    private final AuditLogRepository auditLog;
    private final Duration authArtifactsRetention;
    private final Duration auditLogRetention;

    public DataRetentionService(
            PasswordResetTokenRepository resetTokens,
            LoginAttemptRepository loginAttempts,
            MfaRepository mfa,
            AuditLogRepository auditLog,
            Duration authArtifactsRetention,
            Duration auditLogRetention) {
        this.resetTokens = resetTokens;
        this.loginAttempts = loginAttempts;
        this.mfa = mfa;
        this.auditLog = auditLog;
        this.authArtifactsRetention = authArtifactsRetention;
        this.auditLogRetention = auditLogRetention;
    }

    public Mono<RetentionOutcome> purge(Instant now) {
        Instant authCutoff = now.minus(authArtifactsRetention);
        Instant auditCutoff = now.minus(auditLogRetention);
        return Mono.zip(
                        resetTokens.deleteCreatedBefore(authCutoff).defaultIfEmpty(0L),
                        loginAttempts.deleteLastFailedBefore(authCutoff).defaultIfEmpty(0L),
                        mfa.deleteUsedRecoveryCodesBefore(authCutoff).defaultIfEmpty(0L),
                        auditLog.deleteOccurredBefore(auditCutoff).defaultIfEmpty(0L))
                .map(counts -> new RetentionOutcome(counts.getT1(), counts.getT2(), counts.getT3(), counts.getT4()));
    }
}
