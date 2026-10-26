package com.smxplore.users.infrastructure.adapter.in.dto;

import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProviderReviewRequest(
        @NotNull ProviderRegistrationStatus decision,
        @Size(max = 500) String reason) {}
