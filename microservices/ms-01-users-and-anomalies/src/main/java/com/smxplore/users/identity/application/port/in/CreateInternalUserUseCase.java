package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.CreateInternalUserCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.domain.model.UserId;

public interface CreateInternalUserUseCase {

    UserView create(UserId actorId, CreateInternalUserCommand command);
}
