package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.User;

public interface PasswordResetNotifier {

    void notify(User user, String rawToken);
}
