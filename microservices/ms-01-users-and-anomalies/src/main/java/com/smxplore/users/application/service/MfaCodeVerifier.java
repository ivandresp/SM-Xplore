package com.smxplore.users.application.service;

import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.application.port.out.TotpAuthenticator;
import com.smxplore.users.domain.model.MfaEnrollment;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Verifica un código TOTP o, si no coincide, un código de recuperación de un solo uso.
 */
public final class MfaCodeVerifier {

    private final MfaRepository mfa;
    private final TotpAuthenticator totp;
    private final TokenHasher tokenHasher;

    public MfaCodeVerifier(MfaRepository mfa, TotpAuthenticator totp, TokenHasher tokenHasher) {
        this.mfa = mfa;
        this.totp = totp;
        this.tokenHasher = tokenHasher;
    }

    public Mono<Boolean> verify(UUID userId, MfaEnrollment enrollment, String code, Instant now) {
        String normalized = normalize(code);
        long step = totp.verify(enrollment.secret(), normalized, now);
        if (step >= 0) {
            return mfa.claimStep(userId, step);
        }
        return mfa.consumeRecoveryCode(userId, tokenHasher.hash(recoveryForm(normalized)), now);
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim();
    }

    public static String recoveryForm(String code) {
        return code.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
    }
}
