package com.smxplore.users.application.dto;

import java.time.LocalDate;

public record RegisterGestoraCommand(
        String fullName, String email, String phone, String rawPassword, String nit, LocalDate startOperationDate) {}
