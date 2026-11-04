package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smxplore.users.application.dto.RetentionOutcome;
import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.application.port.out.LoginAttemptRepository;
import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DataRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Duration AUTH_RETENTION = Duration.ofDays(30);
    private static final Duration AUDIT_RETENTION = Duration.ofDays(1825);

    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final LoginAttemptRepository loginAttempts = mock(LoginAttemptRepository.class);
    private final MfaRepository mfa = mock(MfaRepository.class);
    private final AuditLogRepository auditLog = mock(AuditLogRepository.class);
    private final DataRetentionService service =
            new DataRetentionService(resetTokens, loginAttempts, mfa, auditLog, AUTH_RETENTION, AUDIT_RETENTION);

    @Test
    void deletesEachArtifactUsingItsOwnCutoff() {
        when(resetTokens.deleteCreatedBefore(NOW.minus(AUTH_RETENTION))).thenReturn(Mono.just(3L));
        when(loginAttempts.deleteLastFailedBefore(NOW.minus(AUTH_RETENTION))).thenReturn(Mono.just(4L));
        when(mfa.deleteUsedRecoveryCodesBefore(NOW.minus(AUTH_RETENTION))).thenReturn(Mono.just(2L));
        when(auditLog.deleteOccurredBefore(NOW.minus(AUDIT_RETENTION))).thenReturn(Mono.just(1L));

        RetentionOutcome outcome = service.purge(NOW).block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.resetTokens()).isEqualTo(3);
        assertThat(outcome.loginAttempts()).isEqualTo(4);
        assertThat(outcome.recoveryCodes()).isEqualTo(2);
        assertThat(outcome.auditEntries()).isEqualTo(1);
        assertThat(outcome.total()).isEqualTo(10);
        verify(resetTokens).deleteCreatedBefore(NOW.minus(AUTH_RETENTION));
        verify(loginAttempts).deleteLastFailedBefore(NOW.minus(AUTH_RETENTION));
        verify(mfa).deleteUsedRecoveryCodesBefore(NOW.minus(AUTH_RETENTION));
        verify(auditLog).deleteOccurredBefore(NOW.minus(AUDIT_RETENTION));
    }

    @Test
    void treatsAnEmptyDeleteAsZeroRows() {
        when(resetTokens.deleteCreatedBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
        when(loginAttempts.deleteLastFailedBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
        when(mfa.deleteUsedRecoveryCodesBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
        when(auditLog.deleteOccurredBefore(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        RetentionOutcome outcome = service.purge(NOW).block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.total()).isZero();
    }
}
