package com.smxplore.users.identity.application.port.out;

public interface TokenHasher {

    String hash(String rawToken);
}
