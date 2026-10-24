package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.Usuario;

public interface PasswordResetNotifier {

    void notify(Usuario user, String rawToken);
}
