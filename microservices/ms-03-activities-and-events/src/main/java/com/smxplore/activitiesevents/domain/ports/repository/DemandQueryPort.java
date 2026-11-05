package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.indicator.DemandQuery;
import com.smxplore.activitiesevents.domain.model.indicator.DemandRecord;
import reactor.core.publisher.Flux;

public interface DemandQueryPort {

    Flux<DemandRecord> find(DemandQuery query);
}
