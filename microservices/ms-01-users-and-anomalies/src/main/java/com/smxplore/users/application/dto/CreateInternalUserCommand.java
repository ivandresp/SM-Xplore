package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Role;
import java.time.LocalDate;

public record CreateInternalUserCommand(
        Role role,
        String fullName,
        String email,
        String phone,
        String dniType,
        String dniNumber,
        String license,
        String nit,
        LocalDate startOperationDate) {}
