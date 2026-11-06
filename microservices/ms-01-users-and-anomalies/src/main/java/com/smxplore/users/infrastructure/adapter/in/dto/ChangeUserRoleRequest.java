package com.smxplore.users.infrastructure.adapter.in.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ChangeUserRoleRequest(
        @NotBlank String role,
        @Size(max = 20) String dniType,
        @Size(max = 30) String dniNumber,
        @Size(max = 50) String preferredLanguage,
        @Past LocalDate birthDate,
        @Size(max = 80) String nationality,
        @Size(max = 50) String license,
        @Size(max = 30) String nit,
        @Size(max = 50) String businessType,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max = 255) String websiteUrl,
        @PastOrPresent LocalDate startOperationDate) {}
