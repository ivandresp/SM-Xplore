package com.smxplore.users.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pruebas de regresión de MS01-07: los códigos de recuperación se guardan con sal aleatoria por fila. */
class SaltedSha256RecoveryCodeHasherTest {

    private final SaltedSha256RecoveryCodeHasher hasher = new SaltedSha256RecoveryCodeHasher();

    @Test
    void hashesTheSameCodeDifferentlyEachTime() {
        String first = hasher.hash("ABCDE-FGHJK");
        String second = hasher.hash("ABCDE-FGHJK");

        assertThat(first).startsWith("v1$").isNotEqualTo(second);
        assertThat(hasher.matches("ABCDE-FGHJK", first)).isTrue();
        assertThat(hasher.matches("ABCDE-FGHJK", second)).isTrue();
    }

    @Test
    void rejectsACodeThatDoesNotMatchTheStoredHash() {
        String stored = hasher.hash("ABCDE-FGHJK");

        assertThat(hasher.matches("ABCDE-FGHJL", stored)).isFalse();
    }

    @Test
    void rejectsStoredValuesThatAreNotInTheExpectedFormat() {
        assertThat(hasher.matches("ABCDE-FGHJK", null)).isFalse();
        assertThat(hasher.matches("ABCDE-FGHJK", "sin-prefijo")).isFalse();
        assertThat(hasher.matches("ABCDE-FGHJK", "v1$no-base64$tampoco")).isFalse();
    }
}
