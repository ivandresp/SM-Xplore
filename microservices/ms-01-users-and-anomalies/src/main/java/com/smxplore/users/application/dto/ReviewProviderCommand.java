package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ProviderRegistrationStatus;

public record ReviewProviderCommand(ProviderRegistrationStatus decision, String reason) {}
