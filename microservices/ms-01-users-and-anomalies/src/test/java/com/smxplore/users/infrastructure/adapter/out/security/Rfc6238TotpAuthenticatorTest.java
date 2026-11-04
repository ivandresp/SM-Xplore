package com.smxplore.users.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class Rfc6238TotpAuthenticatorTest {

    private static final String RFC_SECRET = base32("12345678901234567890");

    private final Rfc6238TotpAuthenticator totp = new Rfc6238TotpAuthenticator();

    @Test
    void matchesTheRfc6238ReferenceVectors() {
        assertThat(totp.currentCode(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(totp.currentCode(RFC_SECRET, Instant.ofEpochSecond(1_111_111_109)))
                .isEqualTo("081804");
        assertThat(totp.currentCode(RFC_SECRET, Instant.ofEpochSecond(2_000_000_000)))
                .isEqualTo("279037");
    }

    @Test
    void verifiesCodesWithinTheToleranceWindowAndRejectsInvalidOnes() {
        Instant at = Instant.ofEpochSecond(59);

        assertThat(totp.verify(RFC_SECRET, "287082", at)).isEqualTo(1);
        assertThat(totp.verify(RFC_SECRET, "287082", at.plusSeconds(30))).isEqualTo(1);
        assertThat(totp.verify(RFC_SECRET, "000000", at)).isEqualTo(-1);
        assertThat(totp.verify(RFC_SECRET, "28708a", at)).isEqualTo(-1);
        assertThat(totp.verify(RFC_SECRET, "12345", at)).isEqualTo(-1);
    }

    @Test
    void generatesBase32SecretsAndProvisioningUris() {
        String secret = totp.generateSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(totp.currentCode(secret, Instant.now())).hasSize(6);

        String uri = totp.provisioningUri("SM-Xplore", "ada@example.com", secret);
        assertThat(uri)
                .startsWith("otpauth://totp/")
                .contains("secret=" + secret)
                .contains("issuer=SM-Xplore")
                .contains("digits=6")
                .contains("period=30");
    }

    private static String base32(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte current : bytes) {
            buffer = (buffer << 8) | (current & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(alphabet.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(alphabet.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }
}
