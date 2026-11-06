package com.smxplore.securityreports.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserRefDto(
        @NotNull UUID id,
        @NotBlank @Size(max = 100) String name
) {
}
