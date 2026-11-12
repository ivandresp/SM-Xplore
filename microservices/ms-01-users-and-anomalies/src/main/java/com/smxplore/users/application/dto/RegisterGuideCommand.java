package com.smxplore.users.application.dto;

import java.time.LocalDate;

public record RegisterGuideCommand(
        String fullName,
        String email,
        String phone,
        String rawPassword,
        String dniType,
        String dniNumber,
        String license,
        LocalDate startOperationDate) {}
