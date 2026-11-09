package com.smxplore.users.application.port.out;

/**
 * Hashea los códigos de recuperación de MFA.
 *
 * <p>MS01-07: a diferencia de los tokens de recuperación de contraseña (que son aleatorios de 256 bits y no necesitan
 * sal), los códigos de recuperación son cortos y un volcado de la base de datos permitía atacarlos por fuerza bruta
 * offline. Este puerto exige un hash con sal por código.
 */
public interface RecoveryCodeHasher {

    String hash(String rawCode);

    boolean matches(String rawCode, String storedHash);
}
