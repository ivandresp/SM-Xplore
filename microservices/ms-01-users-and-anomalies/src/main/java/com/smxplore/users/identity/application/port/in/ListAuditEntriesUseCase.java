package com.smxplore.users.identity.application.port.in;

import com.smxplore.users.identity.application.dto.AuditEntryView;
import com.smxplore.users.identity.application.dto.AuditSearchCriteria;
import com.smxplore.users.identity.application.dto.PageResult;

public interface ListAuditEntriesUseCase {

    PageResult<AuditEntryView> list(AuditSearchCriteria criteria);
}
