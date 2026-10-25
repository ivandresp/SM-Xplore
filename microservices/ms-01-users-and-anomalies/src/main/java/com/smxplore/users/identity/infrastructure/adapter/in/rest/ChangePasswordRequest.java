package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

record ChangePasswordRequest(
        @NotBlank String currentPassword, @NotBlank String newPassword) {}
