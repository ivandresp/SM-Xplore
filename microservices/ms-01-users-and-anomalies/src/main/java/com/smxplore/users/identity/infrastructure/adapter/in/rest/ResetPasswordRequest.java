package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {}
