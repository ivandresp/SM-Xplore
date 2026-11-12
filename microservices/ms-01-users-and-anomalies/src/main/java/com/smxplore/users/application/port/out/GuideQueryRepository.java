package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.GuideSearchCriteria;
import com.smxplore.users.application.dto.GuideSummaryView;
import com.smxplore.users.application.dto.PageResult;
import reactor.core.publisher.Mono;

/**
 * Consulta de lectura del directorio de guías que revisa una gestora de destinos.
 */
public interface GuideQueryRepository {

    Mono<PageResult<GuideSummaryView>> search(GuideSearchCriteria criteria);
}
