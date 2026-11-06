package com.smxplore.users.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Comparación en tiempo constante de la clave compartida de los endpoints internos.
 *
 * <p>Ambos valores se reducen a su SHA-256 antes de comparar para que el tiempo de respuesta no dependa
 * de la longitud de la clave recibida; {@link MessageDigest#isEqual} recorre los arreglos completos sin
 * cortocircuitar. Así el encabezado {@code X-Internal-Api-Key} no filtra información por temporización.
 */
public final class InternalApiKeyVerifier {

    private InternalApiKeyVerifier() {}

    public static boolean matches(String expected, String provided) {
        if (!configured(expected) || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(digest(expected), digest(provided));
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en la JVM", exception);
        }
    }
}
