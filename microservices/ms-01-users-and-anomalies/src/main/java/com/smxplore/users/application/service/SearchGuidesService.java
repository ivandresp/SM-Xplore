package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.GuideSearchCriteria;
import com.smxplore.users.application.dto.GuideSummaryView;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.in.SearchGuidesUseCase;
import com.smxplore.users.application.port.out.GuideQueryRepository;
import reactor.core.publisher.Mono;

public final class SearchGuidesService implements SearchGuidesUseCase {

    private final GuideQueryRepository guides;

    public SearchGuidesService(GuideQueryRepository guides) {
        this.guides = guides;
    }

    @Override
    public Mono<PageResult<GuideSummaryView>> search(GuideSearchCriteria criteria) {
        return guides.search(criteria);
    }
}
