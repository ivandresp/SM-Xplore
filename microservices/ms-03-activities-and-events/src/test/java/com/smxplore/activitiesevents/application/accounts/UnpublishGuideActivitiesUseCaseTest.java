package com.smxplore.activitiesevents.application.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UnpublishGuideActivitiesUseCaseTest {

    private static final UUID GUIDE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final ActivityRepositoryPort activities = mock(ActivityRepositoryPort.class);
    private final ChangeActivityStatusUseCasePort changeStatus = mock(ChangeActivityStatusUseCasePort.class);
    private final UnpublishGuideActivitiesUseCase useCase =
            new UnpublishGuideActivitiesUseCase(activities, changeStatus);

    @Test
    void unpublishesOnlyPublishedActivitiesOfTheGuide() {
        var published = Activity.builder().guideId(GUIDE).status(ActivityStatus.PUBLISHED).build();
        var draft = Activity.builder().guideId(GUIDE).status(ActivityStatus.DRAFT).build();
        when(activities.findAllByGuideId(GUIDE)).thenReturn(Flux.just(published, draft));
        when(changeStatus.handle(published.getId(), ActivityStatus.PAUSED)).thenReturn(Mono.just(published));

        assertThat(useCase.handle(GUIDE).block()).isEqualTo(1);

        verify(changeStatus).handle(published.getId(), ActivityStatus.PAUSED);
        verify(changeStatus, never()).handle(draft.getId(), ActivityStatus.PAUSED);
    }

    @Test
    void isIdempotentWhenNothingIsPublished() {
        when(activities.findAllByGuideId(GUIDE)).thenReturn(Flux.empty());

        assertThat(useCase.handle(GUIDE).block()).isZero();

        verifyNoInteractions(changeStatus);
    }
}
