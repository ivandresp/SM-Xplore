package com.smxplore.users.application.port.out;

public interface TokenHasher {

    String hash(String rawToken);
}
