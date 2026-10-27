package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.AnomalySearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.domain.model.Anomaly;
import com.smxplore.users.domain.model.AnomalyType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface AnomalyRepository {

    Mono<Anomaly> save(Anomaly anomaly);

    Mono<Anomaly> findById(UUID anomalyId);

    Mono<Anomaly> findLatestByType(AnomalyType type);

    Mono<PageResult<Anomaly>> search(AnomalySearchCriteria criteria);
}
