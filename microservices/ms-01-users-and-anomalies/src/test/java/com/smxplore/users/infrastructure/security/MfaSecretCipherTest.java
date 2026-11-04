package com.smxplore.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MfaSecretCipherTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void encryptsWithAStablePrefixAndDecryptsBackToTheSameSecret() {
        MfaSecretCipher cipher = MfaSecretCipher.ephemeral();

        String encrypted = cipher.encrypt(SECRET);

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain(SECRET);
        assertThat(MfaSecretCipher.isEncrypted(encrypted)).isTrue();
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SECRET);
        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(encrypted);
    }

    @Test
    void keepsLegacyPlaintextReadableSoItCanBeMigrated() {
        MfaSecretCipher cipher = MfaSecretCipher.ephemeral();

        assertThat(MfaSecretCipher.isEncrypted(SECRET)).isFalse();
        assertThat(cipher.decrypt(SECRET)).isEqualTo(SECRET);
    }

    @Test
    void rejectsASecretEncryptedWithAnotherKey() {
        String encrypted = MfaSecretCipher.ephemeral().encrypt(SECRET);
        MfaSecretCipher other = MfaSecretCipher.ephemeral();

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA_SECRET_KEY");
    }

    @Test
    void rejectsKeysThatAreNot256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> MfaSecretCipher.fromBase64Key(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void persistsTheDevelopmentKeySoSecretsSurviveARestart(@TempDir Path directory) {
        Path keyPath = directory.resolve("mfa/secret.key");

        MfaSecretCipher first = MfaSecretCipher.developmentKey(keyPath);
        MfaSecretCipher second = MfaSecretCipher.developmentKey(keyPath);

        assertThat(Files.exists(keyPath)).isTrue();
        assertThat(second.decrypt(first.encrypt(SECRET))).isEqualTo(SECRET);
    }
}
