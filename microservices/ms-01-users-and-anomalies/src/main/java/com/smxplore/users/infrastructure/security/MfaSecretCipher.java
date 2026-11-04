package com.smxplore.users.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cifra los secretos TOTP antes de persistirlos con AES-GCM. El material de la llave nunca se guarda en la base de
 * datos: se toma de {@code MFA_SECRET_KEY} o, en desarrollo, de un archivo local igual que las llaves JWT.
 *
 * <p>El valor almacenado usa el prefijo {@code v1:} seguido de Base64 (IV + texto cifrado + etiqueta). Los valores sin
 * prefijo se consideran legados en texto plano y se vuelven a cifrar la próxima vez que se guardan.
 */
public final class MfaSecretCipher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MfaSecretCipher.class);
    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    private MfaSecretCipher(SecretKey key) {
        this.key = key;
    }

    public static MfaSecretCipher fromBase64Key(String base64Key) {
        byte[] material;
        try {
            material = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "MFA_SECRET_KEY debe ser una llave AES de 32 bytes en Base64", exception);
        }
        if (material.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("MFA_SECRET_KEY debe tener 32 bytes (256 bits) en Base64");
        }
        return new MfaSecretCipher(new SecretKeySpec(material, "AES"));
    }

    public static MfaSecretCipher ephemeral() {
        byte[] material = new byte[KEY_LENGTH_BYTES];
        RANDOM.nextBytes(material);
        return new MfaSecretCipher(new SecretKeySpec(material, "AES"));
    }

    /**
     * Llave de desarrollo persistida en disco para que los secretos sigan siendo legibles tras un reinicio.
     */
    public static MfaSecretCipher developmentKey(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return fromBase64Key(Files.readString(path, StandardCharsets.UTF_8));
            }
            MfaSecretCipher cipher = ephemeral();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(
                    path,
                    cipher.encodedKey(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            restrictPermissions(path);
            LOGGER.warn("Se generó una llave de desarrollo para cifrar los secretos MFA en {}", path.toAbsolutePath());
            return cipher;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo preparar la llave de desarrollo para los secretos MFA", exception);
        }
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cifrar el secreto MFA", exception);
        }
    }

    /**
     * Descifra un valor almacenado. Los valores legados sin prefijo se devuelven tal cual para poder migrarlos.
     */
    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("El secreto MFA almacenado está truncado");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("El secreto MFA almacenado no es Base64 válido", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo descifrar el secreto MFA; ¿cambió MFA_SECRET_KEY?", exception);
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    String encodedKey() {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException exception) {
            LOGGER.debug(
                    "No se pudieron restringir los permisos de {}: {}",
                    path,
                    exception.getClass().getName());
        }
    }
}
