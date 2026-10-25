package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.domain.model.UserId;

public interface AdminResetPasswordUseCase {

    void reset(UserId actorId, UserId targetUserId);
}
