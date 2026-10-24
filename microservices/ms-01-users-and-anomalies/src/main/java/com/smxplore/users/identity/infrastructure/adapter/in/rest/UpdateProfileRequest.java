package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

record UpdateProfileRequest(
        @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,20}$") String phone,
        @Size(max = 50) String preferredLanguage,
        @Past LocalDate birthDate,
        @Size(max = 80) String nationality,
        @Size(max = 50) String businessType,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max = 50) String license) {}
