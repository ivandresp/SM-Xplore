package com.smxplore.users.infrastructure.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.smxplore.users.application.port.out.AuthVersionEvictor;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CaffeineAuthVersionEvictor implements AuthVersionEvictor {

    private final AsyncCache<UUID, Long> cache;

    public CaffeineAuthVersionEvictor(AsyncCache<UUID, Long> cache) {
        this.cache = cache;
    }

    @Override
    public Mono<Void> evict(UUID userId) {
        cache.synchronous().invalidate(userId);
        return Mono.empty();
    }
}
