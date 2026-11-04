package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateInternalUserRequest(
        @NotBlank String role,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,20}$") String phone,
        @Size(max = 20) String dniType,
        @Size(max = 30) String dniNumber,
        @Size(max = 50) String license,
        @Pattern(regexp = "^$|^[0-9\\-]{6,30}$") String nit,
        @PastOrPresent LocalDate startOperationDate) {}
