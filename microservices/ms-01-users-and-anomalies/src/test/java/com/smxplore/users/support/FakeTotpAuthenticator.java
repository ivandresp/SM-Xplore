package com.smxplore.users.support;

import com.smxplore.users.application.port.out.TotpAuthenticator;
import java.time.Instant;

public final class FakeTotpAuthenticator implements TotpAuthenticator {

    private String expectedCode = "123456";
    private long step = 42L;

    public FakeTotpAuthenticator expectedCode(String value) {
        this.expectedCode = value;
        return this;
    }

    public FakeTotpAuthenticator step(long value) {
        this.step = value;
        return this;
    }

    @Override
    public String generateSecret() {
        return "JBSWY3DPEHPK3PXP";
    }

    @Override
    public String currentCode(String secret, Instant at) {
        return expectedCode;
    }

    @Override
    public long verify(String secret, String code, Instant at) {
        return expectedCode.equals(code) ? step : -1L;
    }

    @Override
    public String provisioningUri(String issuer, String accountName, String secret) {
        return "otpauth://totp/" + issuer + ":" + accountName + "?secret=" + secret;
    }
}
