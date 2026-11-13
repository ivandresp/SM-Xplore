package com.smxplore.activitiesevents.application.accounts;

import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Cascada de B3: cuando una cuenta de guia se bloquea, sus actividades publicadas dejan de
 * mostrarse en el catalogo. Solo toca PUBLISHED (idempotente: un segundo evento no hace nada) y
 * usa PAUSED, no DRAFT, porque PAUSED no se publica y el desbloqueo no republica solo.
 */
@RequiredArgsConstructor
public class UnpublishGuideActivitiesUseCase {

    private final ActivityRepositoryPort activities;
    private final ChangeActivityStatusUseCasePort changeStatus;

    public Mono<Integer> handle(UUID guideId) {
        return activities.findAllByGuideId(guideId)
                .filter(activity -> activity.getStatus() == ActivityStatus.PUBLISHED)
                .concatMap(activity -> changeStatus.handle(activity.getId(), ActivityStatus.PAUSED))
                .count()
                .map(Long::intValue);
    }
}
