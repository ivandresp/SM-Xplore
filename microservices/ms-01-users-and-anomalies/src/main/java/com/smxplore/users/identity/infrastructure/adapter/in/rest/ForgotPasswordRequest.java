package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record ForgotPasswordRequest(@NotBlank @Email String email) {}
