package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserRefDto(
        @NotNull UUID id,
        @NotBlank String name
) {
}
