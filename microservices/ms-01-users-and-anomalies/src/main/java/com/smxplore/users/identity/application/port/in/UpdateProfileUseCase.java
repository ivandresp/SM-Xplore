package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.UpdateProfileCommand;
import com.smxplore.users.identity.application.dto.UserView;
import com.smxplore.users.identity.domain.model.UserId;

public interface UpdateProfileUseCase {

    UserView update(UserId userId, UpdateProfileCommand command);
}
