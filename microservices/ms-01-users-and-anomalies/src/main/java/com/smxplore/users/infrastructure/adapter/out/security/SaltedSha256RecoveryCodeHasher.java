package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.port.out.RecoveryCodeHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Hash con sal por código de recuperación: {@code v1$<sal Base64>$<SHA-256(sal || código) Base64>}.
 *
 * <p>MS01-07: el almacenamiento anterior era un SHA-256 sin sal del código completo, así que quien obtuviera un volcado
 * de {@code mfa_recovery_codes} podía probar candidatos offline a gran velocidad y, además, dos usuarios con el mismo
 * código compartían hash. Con sal aleatoria de 16 bytes por código cada fila exige un ataque independiente y no se
 * puede correlacionar entre usuarios.
 */
@Component
public class SaltedSha256RecoveryCodeHasher implements RecoveryCodeHasher {

    private static final String PREFIX = "v1$";
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Base64.Encoder encoder = Base64.getEncoder();

    private final Base64.Decoder decoder = Base64.getDecoder();

    @Override
    public String hash(String rawCode) {
        String normalized = rawCode == null ? "" : rawCode;
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return PREFIX + encoder.encodeToString(salt) + "$" + encoder.encodeToString(digest(salt, normalized));
    }

    @Override
    public boolean matches(String rawCode, String storedHash) {
        if (rawCode == null || storedHash == null || !storedHash.startsWith(PREFIX)) {
            return false;
        }
        int separator = storedHash.indexOf('$', PREFIX.length());
        if (separator < 0) {
            return false;
        }
        try {
            byte[] salt = decoder.decode(storedHash.substring(PREFIX.length(), separator));
            byte[] expected = decoder.decode(storedHash.substring(separator + 1));
            byte[] actual = digest(salt, rawCode);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] digest(byte[] salt, String rawCode) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(salt);
            messageDigest.update(rawCode.getBytes(StandardCharsets.UTF_8));
            return messageDigest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en la JVM", exception);
        }
    }
}
