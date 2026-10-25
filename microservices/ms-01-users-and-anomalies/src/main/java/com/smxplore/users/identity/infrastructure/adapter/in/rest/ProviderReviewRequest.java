package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ProviderReviewRequest(
        @NotNull ProviderRegistrationStatus decision,
        @Size(max = 500) String reason) {}
