package com.smxplore.users.support;

import com.smxplore.users.application.port.out.MfaEnabledNotifier;
import com.smxplore.users.domain.model.Usuario;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

public final class FakeMfaEnabledNotifier implements MfaEnabledNotifier {

    private final List<Usuario> notified = new ArrayList<>();

    public List<Usuario> notified() {
        return notified;
    }

    @Override
    public Mono<Void> notify(Usuario user) {
        notified.add(user);
        return Mono.empty();
    }
}
