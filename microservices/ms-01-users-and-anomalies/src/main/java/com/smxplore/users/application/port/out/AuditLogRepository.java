package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.domain.model.AuditEntry;
import java.time.Instant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuditLogRepository {

    Mono<AuditEntry> save(AuditEntry entry);

    Mono<PageResult<AuditEntry>> search(AuditSearchCriteria criteria);

    Flux<AuditEntry> export(AuditSearchCriteria criteria, int limit);

    /**
     * Elimina las entradas anteriores al corte de retención. Devuelve la cantidad de filas borradas.
     */
    Mono<Long> deleteOccurredBefore(Instant cutoff);
}
