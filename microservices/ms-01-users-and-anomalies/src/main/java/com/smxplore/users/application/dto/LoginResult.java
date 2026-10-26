package com.smxplore.users.application.dto;

import java.time.Instant;

public record LoginResult(String accessToken, String tokenType, long expiresInSeconds, Instant expiresAt) {}
