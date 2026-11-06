package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.port.out.RecoveryCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomRecoveryCodeGenerator implements RecoveryCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;
    private static final int GROUP = 4;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int index = 0; index < LENGTH; index++) {
            if (index == GROUP) {
                code.append('-');
            }
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
