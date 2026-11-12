package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import com.smxplore.users.application.dto.RegistrationSummaryView;
import reactor.core.publisher.Mono;

/**
 * Consulta de lectura de las altas sometidas a revisión (guías y prestadoras de servicios).
 */
public interface RegistrationRepository {

    Mono<PageResult<RegistrationSummaryView>> search(RegistrationSearchCriteria criteria);
}
