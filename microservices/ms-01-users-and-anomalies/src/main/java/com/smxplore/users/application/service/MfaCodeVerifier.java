package com.smxplore.users.application.service;

import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.TotpAuthenticator;
import com.smxplore.users.domain.model.MfaEnrollment;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class MfaCodeVerifier {

    private final MfaRepository mfa;
    private final TotpAuthenticator totp;

    public MfaCodeVerifier(MfaRepository mfa, TotpAuthenticator totp) {
        this.mfa = mfa;
        this.totp = totp;
    }

    /**
     * Valida un código TOTP o, si no es TOTP, un código de recuperación.
     *
     * <p>MS01-07: el código de recuperación se entrega al repositorio en claro (normalizado) para que el adaptador lo
     * compare contra cada hash con sal. Antes se hasheaba aquí con un SHA-256 sin sal compartido con los tokens de
     * recuperación de contraseña.
     */
    public Mono<Boolean> verify(UUID userId, MfaEnrollment enrollment, String code, Instant now) {
        String normalized = normalize(code);
        long step = totp.verify(enrollment.secret(), normalized, now);
        if (step >= 0) {
            return mfa.claimStep(userId, step);
        }
        return mfa.consumeRecoveryCode(userId, recoveryForm(normalized), now);
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim();
    }

    /** Forma canónica de un código de recuperación: sin espacios ni guiones y en mayúsculas. */
    public static String recoveryForm(String code) {
        return code.replace(" ", "").replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }
}
