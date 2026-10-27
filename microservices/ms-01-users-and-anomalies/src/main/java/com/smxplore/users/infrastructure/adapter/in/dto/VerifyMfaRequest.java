package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyMfaRequest(
        @NotBlank String challengeToken, @NotBlank String code) {}
