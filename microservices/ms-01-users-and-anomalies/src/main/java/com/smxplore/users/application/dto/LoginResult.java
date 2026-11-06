package com.smxplore.users.application.dto;

import java.time.Instant;

public record LoginResult(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        boolean mfaRequired,
        String mfaChallengeToken) {

    public static LoginResult access(String accessToken, long expiresInSeconds, Instant expiresAt) {
        return new LoginResult(accessToken, "Bearer", expiresInSeconds, expiresAt, false, null);
    }

    public static LoginResult mfaChallenge(String challengeToken, long expiresInSeconds, Instant expiresAt) {
        return new LoginResult(null, null, expiresInSeconds, expiresAt, true, challengeToken);
    }
}
