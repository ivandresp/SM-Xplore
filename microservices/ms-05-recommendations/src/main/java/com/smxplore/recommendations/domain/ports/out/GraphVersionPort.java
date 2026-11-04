package com.smxplore.recommendations.domain.ports.out;

import reactor.core.publisher.Mono;

/**
 * Version of the projected graph.
 *
 * <p>Every applied projection increments it, which is what invalidates cached recommendations
 * without walking the cache. The version is only a coordination hint: if it cannot be read the
 * cache falls back to its time to live.
 */
public interface GraphVersionPort {

    Mono<Long> current();

    Mono<Void> bump();
}
