package com.smxplore.users.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Pruebas de regresión de MS01-07: los códigos de recuperación tienen 160 bits de entropía. */
class SecureRandomRecoveryCodeGeneratorTest {

    private final SecureRandomRecoveryCodeGenerator generator = new SecureRandomRecoveryCodeGenerator();

    @Test
    void generatesThirtyTwoBase32CharactersGroupedInFours() {
        String code = generator.generate();

        assertThat(code.replace("-", "")).hasSize(32);
        assertThat(code.split("-", -1)).allSatisfy(group -> assertThat(group).hasSize(4));
        assertThat(code.replace("-", "")).matches("[A-Z2-7]{32}");
    }

    @Test
    void generatesDifferentCodesOnEachCall() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
        assertThat(Arrays.stream(new String[] {generator.generate(), generator.generate(), generator.generate()})
                        .distinct()
                        .count())
                .isEqualTo(3);
    }
}
