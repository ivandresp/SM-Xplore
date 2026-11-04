package com.smxplore.users.domain.policy;

import java.time.Duration;

/**
 * Reglas de bloqueo temporal de una cuenta tras varios intentos de acceso fallidos.
 */
public record LoginLockoutPolicy(int maxAttempts, Duration window, Duration duration) {

    public LoginLockoutPolicy {
        if (maxAttempts < 2) {
            throw new IllegalArgumentException("El número máximo de intentos fallidos debe ser al menos 2");
        }
        requirePositive(window, "La ventana de intentos fallidos");
        requirePositive(duration, "La duración del bloqueo");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " debe ser positiva");
        }
    }
}
