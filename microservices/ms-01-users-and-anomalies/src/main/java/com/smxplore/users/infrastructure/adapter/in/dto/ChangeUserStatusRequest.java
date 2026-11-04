package com.smxplore.users.infrastructure.adapter.in.dto;

import com.smxplore.users.domain.model.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeUserStatusRequest(
        @NotNull UserStatus status, @Size(max = 500) String reason) {}
