package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.ResetPasswordCommand;

public interface ResetPasswordUseCase {

    void reset(ResetPasswordCommand command);
}
