package com.smxplore.users.application.port.out;

import java.time.Instant;

public interface TotpAuthenticator {

    String generateSecret();

    String currentCode(String secret, Instant at);

    /**
     * Verifica un código TOTP con una ventana de tolerancia. Devuelve el paso de tiempo que coincidió o {@code -1}.
     */
    long verify(String secret, String code, Instant at);

    String provisioningUri(String issuer, String accountName, String secret);
}
