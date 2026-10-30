package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AnomalySearchCriteria;
import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.PageResult;
import reactor.core.publisher.Mono;

public interface ListAnomaliesUseCase {

    Mono<PageResult<AnomalyView>> list(AnomalySearchCriteria criteria);
}
