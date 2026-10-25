package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.UserStatus;

public record ChangeUserStatusCommand(UserStatus status, String reason) {}
