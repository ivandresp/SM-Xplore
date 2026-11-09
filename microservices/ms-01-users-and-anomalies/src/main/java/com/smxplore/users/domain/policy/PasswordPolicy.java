package com.smxplore.users.domain.policy;

import com.smxplore.users.domain.exception.ValidationException;
import java.util.Locale;
import java.util.Set;

/**
 * Política de contraseñas de la plataforma.
 *
 * <p>MS01-19: además de la longitud se exige una combinación mínima de tipos de caracteres y se rechazan las
 * contraseñas más comunes. Las contraseñas demasiado débiles devuelven un mensaje genérico para no revelar exactamente
 * qué regla falló.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;
    public static final int MIN_CHARACTER_CLASSES = 3;

    private static final String GENERIC_MESSAGE =
            "La contraseña no cumple la política de seguridad: usa al menos 12 caracteres y combina al menos tres de "
                    + "mayúsculas, minúsculas, dígitos y símbolos";

    /** Lista corta de contraseñas comunes o filtradas en campañas reales; se comparan en minúsculas. */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012",
            "1234567890123",
            "12345678901234",
            "password1234",
            "password12345",
            "qwertyuiop12",
            "aaaaaaaaaaaa",
            "contraseña123",
            "admin123456789",
            "qwerty123456",
            "iloveyou1234",
            "bienvenido123",
            "santamarta123",
            "turismo123456");

    private PasswordPolicy() {}

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("La contraseña es obligatoria");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new ValidationException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new ValidationException("La contraseña no puede superar " + MAX_LENGTH + " caracteres");
        }
        if (COMMON_PASSWORDS.contains(rawPassword.toLowerCase(Locale.ROOT))) {
            throw new ValidationException(GENERIC_MESSAGE);
        }
        if (characterClasses(rawPassword) < MIN_CHARACTER_CLASSES) {
            throw new ValidationException(GENERIC_MESSAGE);
        }
    }

    private static int characterClasses(String rawPassword) {
        int classes = 0;
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(Character::isDigit)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(character -> !Character.isLetterOrDigit(character))) {
            classes++;
        }
        return classes;
    }
}
