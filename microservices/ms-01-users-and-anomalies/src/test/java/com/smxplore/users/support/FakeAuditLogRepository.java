package com.smxplore.users.support;

import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.domain.model.AuditEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class FakeAuditLogRepository implements AuditLogRepository {

    private final List<AuditEntry> saved = new ArrayList<>();

    public List<AuditEntry> saved() {
        return saved;
    }

    @Override
    public Mono<AuditEntry> save(AuditEntry entry) {
        saved.add(entry);
        return Mono.just(entry);
    }

    @Override
    public Mono<PageResult<AuditEntry>> search(AuditSearchCriteria criteria) {
        return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Flux<AuditEntry> export(AuditSearchCriteria criteria, int limit) {
        return Flux.fromIterable(saved);
    }

    @Override
    public Mono<Long> deleteOccurredBefore(Instant cutoff) {
        return Mono.just(0L);
    }
}
