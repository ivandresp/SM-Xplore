package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.indicator.DemandQuery;
import com.smxplore.sitesandservices.domain.model.indicator.DemandRecord;
import reactor.core.publisher.Flux;

public interface DemandQueryPort {

    Flux<DemandRecord> find(DemandQuery query);
}
