package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.port.out.RecoveryCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Genera códigos de recuperación de 160 bits en Base32 (32 caracteres), agrupados de 4 en 4.
 *
 * <p>MS01-07: antes el código era de 8 caracteres de un alfabeto de 32 símbolos (32^8 = 2^40, unos 40 bits), lo que
 * hacía viable un ataque offline sobre un volcado de la base de datos. Con 20 bytes aleatorios la entropía sube a 160
 * bits y el formato sigue siendo fácil de transcribir a mano.
 */
@Component
public class SecureRandomRecoveryCodeGenerator implements RecoveryCodeGenerator {

    private static final int SECRET_BYTES = 20;

    private static final int GROUP_SIZE = 4;

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return group(encodeBase32(bytes));
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder encoded = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                encoded.append(ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            encoded.append(ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return encoded.toString();
    }

    private static String group(String code) {
        StringBuilder grouped = new StringBuilder(code.length() + code.length() / GROUP_SIZE);
        for (int index = 0; index < code.length(); index++) {
            if (index > 0 && index % GROUP_SIZE == 0) {
                grouped.append('-');
            }
            grouped.append(code.charAt(index));
        }
        return grouped.toString();
    }
}
