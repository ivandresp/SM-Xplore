package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.RegisterProviderCommand;
import com.smxplore.users.identity.application.dto.UserView;

public interface RegisterProviderUseCase {

    UserView register(RegisterProviderCommand command);
}
