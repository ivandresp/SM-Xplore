package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.ForgotPasswordCommand;

public interface ForgotPasswordUseCase {

    void handle(ForgotPasswordCommand command);
}
