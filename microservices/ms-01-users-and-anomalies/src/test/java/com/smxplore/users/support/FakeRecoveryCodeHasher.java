package com.smxplore.users.support;

import com.smxplore.users.application.port.out.RecoveryCodeHasher;

/** Hasher de códigos de recuperación determinista para pruebas (MS01-07). */
public class FakeRecoveryCodeHasher implements RecoveryCodeHasher {

    private static final String PREFIX = "fake$";

    @Override
    public String hash(String rawCode) {
        return PREFIX + rawCode;
    }

    @Override
    public boolean matches(String rawCode, String storedHash) {
        return rawCode != null && storedHash != null && storedHash.equals(PREFIX + rawCode);
    }
}
