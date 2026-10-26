package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AuditEntryView;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import reactor.core.publisher.Mono;

public interface ListAuditEntriesUseCase {

    Mono<PageResult<AuditEntryView>> list(AuditSearchCriteria criteria);
}
