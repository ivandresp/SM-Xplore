package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.BootstrapAdminCommand;

public interface BootstrapAdminUseCase {

    void bootstrap(BootstrapAdminCommand command);
}
