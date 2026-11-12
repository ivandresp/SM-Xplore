package com.smxplore.users.infrastructure.adapter.in.dto;

import com.smxplore.users.domain.model.RegistrationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrationReviewRequest(
        @NotNull RegistrationStatus decision,
        @Size(max = 500) String reason) {}
