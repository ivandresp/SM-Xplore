package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AnomalySearchCriteria;
import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.in.ListAnomaliesUseCase;
import com.smxplore.users.application.port.out.AnomalyRepository;
import reactor.core.publisher.Mono;

public final class ListAnomaliesService implements ListAnomaliesUseCase {

    private final AnomalyRepository anomalies;

    public ListAnomaliesService(AnomalyRepository anomalies) {
        this.anomalies = anomalies;
    }

    @Override
    public Mono<PageResult<AnomalyView>> list(AnomalySearchCriteria criteria) {
        return anomalies
                .search(criteria)
                .map(page -> PageResult.of(
                        page.content().stream().map(AnomalyView::from).toList(),
                        page.page(),
                        page.size(),
                        page.totalElements()));
    }
}
