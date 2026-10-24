package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record RegisterTouristRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,

        @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$")
        String phone,

        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 20) String dniType,
        @NotBlank @Size(max = 30) String dniNumber,
        @Size(max = 50) String preferredLanguage,
        @Past LocalDate birthDate,
        @Size(max = 80) String nationality) {}
