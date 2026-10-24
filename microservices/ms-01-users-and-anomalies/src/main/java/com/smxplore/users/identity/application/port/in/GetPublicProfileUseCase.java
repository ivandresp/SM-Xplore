package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.PublicProfileView;
import com.smxplore.users.identity.domain.model.UserId;

public interface GetPublicProfileUseCase {

    PublicProfileView get(UserId userId);
}
