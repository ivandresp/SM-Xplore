package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.Usuario;

public interface ProviderReviewNotifier {

    void notify(Usuario provider, ProviderRegistrationStatus decision, String reason);
}
