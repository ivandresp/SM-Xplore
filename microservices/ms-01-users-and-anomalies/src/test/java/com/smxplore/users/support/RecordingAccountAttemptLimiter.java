package com.smxplore.users.support;

import com.smxplore.users.application.port.out.AccountAttemptLimiter;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class RecordingAccountAttemptLimiter implements AccountAttemptLimiter {

    private final int maxAttempts;
    private final Map<String, Integer> failures = new HashMap<>();
    private final List<String> failureKeys = new ArrayList<>();
    private final List<String> ensuredKeys = new ArrayList<>();
    private final List<String> clearedKeys = new ArrayList<>();

    public RecordingAccountAttemptLimiter(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public List<String> failureKeys() {
        return failureKeys;
    }

    public List<String> ensuredKeys() {
        return ensuredKeys;
    }

    public List<String> clearedKeys() {
        return clearedKeys;
    }

    @Override
    public Mono<Void> ensureAllowed(String key) {
        ensuredKeys.add(key);
        if (failures.getOrDefault(key, 0) >= maxAttempts) {
            return Mono.error(new TooManyLoginAttemptsException(Duration.ofMinutes(1)));
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> recordFailure(String key) {
        failureKeys.add(key);
        failures.merge(key, 1, Integer::sum);
        return Mono.empty();
    }

    @Override
    public Mono<Void> clear(String key) {
        clearedKeys.add(key);
        failures.remove(key);
        return Mono.empty();
    }
}
