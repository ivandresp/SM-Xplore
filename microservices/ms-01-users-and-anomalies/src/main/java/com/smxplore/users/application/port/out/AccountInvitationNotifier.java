package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

public interface AccountInvitationNotifier {

    Mono<Void> notify(Usuario user, String rawToken);
}
