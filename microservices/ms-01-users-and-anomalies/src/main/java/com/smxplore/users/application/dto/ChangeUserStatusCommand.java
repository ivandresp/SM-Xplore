package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.UserStatus;

public record ChangeUserStatusCommand(UserStatus status, String reason, Role actorRole) {}
