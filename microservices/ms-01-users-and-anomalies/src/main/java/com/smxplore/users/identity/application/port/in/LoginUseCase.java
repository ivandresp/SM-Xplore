package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.LoginCommand;
import com.smxplore.users.identity.application.dto.LoginResult;

public interface LoginUseCase {

    LoginResult login(LoginCommand command);
}
