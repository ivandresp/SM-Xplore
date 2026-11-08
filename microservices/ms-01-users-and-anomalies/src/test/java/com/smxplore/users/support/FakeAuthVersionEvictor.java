package com.smxplore.users.support;

import com.smxplore.users.application.port.out.AuthVersionEvictor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class FakeAuthVersionEvictor implements AuthVersionEvictor {

    private final List<UUID> evicted = new ArrayList<>();

    public List<UUID> evicted() {
        return evicted;
    }

    @Override
    public Mono<Void> evict(UUID userId) {
        evicted.add(userId);
        return Mono.empty();
    }
}
