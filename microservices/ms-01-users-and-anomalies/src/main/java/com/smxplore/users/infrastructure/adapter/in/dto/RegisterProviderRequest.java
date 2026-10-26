package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterProviderRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,

        @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$")
        String phone,

        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Pattern(regexp = "^[0-9\\-]{6,30}$") String nit,
        @Size(max = 50) String businessType,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @PastOrPresent LocalDate startOperationDate) {}
