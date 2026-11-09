package com.smxplore.users.support;

import com.smxplore.users.application.port.out.AccountLockedNotifier;
import com.smxplore.users.domain.model.Email;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

/** Notificador de bloqueos de cuenta en memoria para pruebas (MS01-05). */
public class FakeAccountLockedNotifier implements AccountLockedNotifier {

    private final List<Email> notified = new ArrayList<>();

    @Override
    public Mono<Void> notify(Email email, Duration remaining) {
        notified.add(email);
        return Mono.empty();
    }

    public List<Email> notified() {
        return notified;
    }
}
