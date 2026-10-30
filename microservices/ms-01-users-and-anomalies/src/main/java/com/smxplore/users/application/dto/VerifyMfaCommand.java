package com.smxplore.users.application.dto;

public record VerifyMfaCommand(String challengeToken, String code) {}
