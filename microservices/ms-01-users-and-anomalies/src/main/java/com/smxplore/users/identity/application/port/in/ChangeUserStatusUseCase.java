package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.identity.domain.model.UserId;

public interface ChangeUserStatusUseCase {

    void change(UserId actorId, UserId targetUserId, ChangeUserStatusCommand command);
}
