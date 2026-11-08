package com.smxplore.users.support;

import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.domain.model.MfaEnrollment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class FakeMfaRepository implements MfaRepository {

    private MfaEnrollment enrollment;
    private final List<MfaEnrollment> saved = new ArrayList<>();
    private final Set<Long> claimedSteps = new HashSet<>();
    private final List<String> recoveryHashes = new ArrayList<>();

    public FakeMfaRepository seed(MfaEnrollment value) {
        this.enrollment = value;
        return this;
    }

    public MfaEnrollment enrollment() {
        return enrollment;
    }

    public List<MfaEnrollment> saved() {
        return saved;
    }

    public List<String> recoveryHashes() {
        return recoveryHashes;
    }

    @Override
    public Mono<MfaEnrollment> findByUserId(UUID userId) {
        return Mono.justOrEmpty(enrollment);
    }

    @Override
    public Mono<Void> save(MfaEnrollment value) {
        this.enrollment = value;
        this.saved.add(value);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteByUserId(UUID userId) {
        this.enrollment = null;
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> claimStep(UUID userId, long step) {
        return Mono.just(claimedSteps.add(step));
    }

    @Override
    public Mono<Void> replaceRecoveryCodes(UUID userId, List<String> codeHashes, Instant now) {
        recoveryHashes.clear();
        recoveryHashes.addAll(codeHashes);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> consumeRecoveryCode(UUID userId, String codeHash, Instant now) {
        return Mono.just(false);
    }

    @Override
    public Mono<Long> deleteUsedRecoveryCodesBefore(Instant cutoff) {
        return Mono.just(0L);
    }
}
