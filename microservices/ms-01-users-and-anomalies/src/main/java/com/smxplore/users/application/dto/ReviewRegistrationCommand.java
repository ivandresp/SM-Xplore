package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.RegistrationStatus;

public record ReviewRegistrationCommand(RegistrationStatus decision, String reason) {}
