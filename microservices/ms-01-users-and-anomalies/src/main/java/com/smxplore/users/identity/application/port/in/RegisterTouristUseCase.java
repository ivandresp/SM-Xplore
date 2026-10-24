package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.RegisterTouristCommand;
import com.smxplore.users.identity.application.dto.UserView;

public interface RegisterTouristUseCase {

    UserView register(RegisterTouristCommand command);
}
