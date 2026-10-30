package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;

public record DisableMfaRequest(
        @NotBlank String password, @NotBlank String code) {}
