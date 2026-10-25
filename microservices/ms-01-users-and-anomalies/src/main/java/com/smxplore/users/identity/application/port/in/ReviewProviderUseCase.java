package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.ReviewProviderCommand;
import com.smxplore.users.identity.domain.model.UserId;

public interface ReviewProviderUseCase {

    void review(UserId actorId, UserId targetUserId, ReviewProviderCommand command);
}
