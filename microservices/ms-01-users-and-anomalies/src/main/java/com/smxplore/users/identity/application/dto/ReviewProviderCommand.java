package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;

public record ReviewProviderCommand(ProviderRegistrationStatus decision, String reason) {}
