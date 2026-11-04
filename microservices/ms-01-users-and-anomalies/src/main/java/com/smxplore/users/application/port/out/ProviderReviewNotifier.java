package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

public interface ProviderReviewNotifier {

    Mono<Void> notify(Usuario provider, ProviderRegistrationStatus decision, String reason);
}
