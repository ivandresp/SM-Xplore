package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.port.out.TotpAuthenticator;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Implementación propia de TOTP (RFC 6238) con HMAC-SHA1 y ventana de ±1 paso, sin dependencias externas.
 */
@Component
public class Rfc6238TotpAuthenticator implements TotpAuthenticator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20;
    private static final int TOLERANCE_STEPS = 1;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    @Override
    public String currentCode(String secret, Instant at) {
        return code(secret, stepAt(at));
    }

    @Override
    public long verify(String secret, String code, Instant at) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        long currentStep = stepAt(at);
        byte[] provided = code.getBytes(StandardCharsets.US_ASCII);
        for (long candidate = currentStep - TOLERANCE_STEPS; candidate <= currentStep + TOLERANCE_STEPS; candidate++) {
            if (candidate < 0) {
                continue;
            }
            byte[] expected = code(secret, candidate).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(provided, expected)) {
                return candidate;
            }
        }
        return -1;
    }

    @Override
    public String provisioningUri(String issuer, String accountName, String secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String label = URLEncoder.encode(issuer + ":" + accountName, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encodedIssuer + "&algorithm=SHA1&digits="
                + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    private static long stepAt(Instant at) {
        return at.getEpochSecond() / TIME_STEP_SECONDS;
    }

    private static String code(String secret, long step) {
        try {
            byte[] key = base32Decode(secret);
            byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo calcular el código TOTP", exception);
        }
    }

    private static String base32Encode(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private static byte[] base32Decode(String secret) {
        String normalized =
                secret.trim().replace(" ", "").replace("-", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char character : normalized.toCharArray()) {
            int value = ALPHABET.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("El secreto TOTP no está en base32");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result.toByteArray();
    }
}
