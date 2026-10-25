package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.domain.model.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ChangeUserStatusRequest(
        @NotNull UserStatus status, @Size(max = 500) String reason) {}
