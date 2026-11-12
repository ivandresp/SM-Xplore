package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.RegistrationPage;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import reactor.core.publisher.Mono;

/**
 * Consulta de lectura de las altas sometidas a revisión (guías y prestadoras de servicios).
 */
public interface RegistrationRepository {

    Mono<RegistrationPage> search(RegistrationSearchCriteria criteria);
}
