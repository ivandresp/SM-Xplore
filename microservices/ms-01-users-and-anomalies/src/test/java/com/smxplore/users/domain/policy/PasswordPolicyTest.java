package com.smxplore.users.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

/** Regresión de MS01-19: la política de contraseñas exige longitud, variedad de caracteres y rechaza claves comunes. */
class PasswordPolicyTest {

    @Test
    void acceptsAStrongPassword() {
        assertThatCode(() -> PasswordPolicy.validate("ContraseñaSegura1!")).doesNotThrowAnyException();
    }

    @Test
    void acceptsATwelveCharacterPasswordWithThreeClasses() {
        assertThatCode(() -> PasswordPolicy.validate("Abcdefghijk1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsShorterThanTheMinimum() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abc1!"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("al menos 12 caracteres");
    }

    @Test
    void rejectsPasswordsLongerThanTheMaximum() {
        String tooLong = "Abcdefghijk1" + "x".repeat(61);

        assertThatThrownBy(() -> PasswordPolicy.validate(tooLong))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no puede superar 72 caracteres");
    }

    @Test
    void rejectsARepeatedCharacterPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("aaaaaaaaaaaa"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("política de seguridad");
    }

    @Test
    void rejectsACommonNumericPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("123456789012"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("política de seguridad");
    }

    @Test
    void rejectsAPasswordWithOnlyTwoCharacterClasses() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdefghijkl"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("política de seguridad");
    }
}
