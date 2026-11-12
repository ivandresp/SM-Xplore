package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.GuideSearchCriteria;
import com.smxplore.users.application.dto.GuideSummaryView;
import com.smxplore.users.application.dto.PageResult;
import reactor.core.publisher.Mono;

public interface SearchGuidesUseCase {

    Mono<PageResult<GuideSummaryView>> search(GuideSearchCriteria criteria);
}
