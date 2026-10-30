package com.smxplore.users.domain.policy;

import com.smxplore.users.domain.exception.ValidationException;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {}

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("La contraseña es obligatoria");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new ValidationException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new ValidationException("La contraseña no puede superar " + MAX_LENGTH + " caracteres");
        }
    }
}
