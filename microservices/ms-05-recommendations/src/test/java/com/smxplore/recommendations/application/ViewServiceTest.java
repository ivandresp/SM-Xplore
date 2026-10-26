package com.smxplore.recommendations.application;

import com.smxplore.recommendations.domain.exception.ItemNotFoundException;
import com.smxplore.recommendations.domain.model.ItemType;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();

    @Mock
    private RecommendationGraphPort graphPort;

    private ViewService service;

    @BeforeEach
    void setUp() {
        service = new ViewService(graphPort, Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registersViewForExistingItem() {
        when(graphPort.recordView(eq(TOURIST), eq(ItemType.EVENT), eq(ITEM), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.registerView(TOURIST, ItemType.EVENT, ITEM, "es"))
                .verifyComplete();
    }

    @Test
    void rejectsUnknownItem() {
        when(graphPort.recordView(eq(TOURIST), eq(ItemType.EVENT), eq(ITEM), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.registerView(TOURIST, ItemType.EVENT, ITEM, "es"))
                .expectError(ItemNotFoundException.class)
                .verify();
    }
}
