package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.ChangePasswordCommand;
import com.smxplore.users.identity.domain.model.UserId;

public interface ChangeOwnPasswordUseCase {

    void change(UserId userId, ChangePasswordCommand command);
}
