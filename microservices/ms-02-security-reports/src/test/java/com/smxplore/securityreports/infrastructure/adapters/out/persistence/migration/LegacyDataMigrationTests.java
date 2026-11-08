package com.smxplore.securityreports.infrastructure.adapters.out.persistence.migration;

import com.mongodb.client.result.UpdateResult;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.LocationDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import com.smxplore.securityreports.infrastructure.config.MigrationProperties;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS02-16: el backfill heredado corre por lotes, respeta el marcador de completado y no bloquea
 * el arranque si se queda sin presupuesto de tiempo.
 */
class LegacyDataMigrationTests {

    private static final String MARKERS = "security_report_migrations";

    private final ReactiveMongoOperations mongoOperations = mock(ReactiveMongoOperations.class);

    @Test
    void doesNothingWhenTheMigrationIsDisabled() {
        migration(false).run(null);

        verifyNoInteractions(mongoOperations);
    }

    @Test
    void skipsTheBackfillWhenTheCompletionMarkerAlreadyExists() {
        when(mongoOperations.exists(any(Query.class), eq(MARKERS))).thenReturn(Mono.just(true));

        migration(true).run(null);

        verify(mongoOperations).exists(any(Query.class), eq(MARKERS));
        verify(mongoOperations, never()).updateMulti(any(Query.class), any(Update.class), any(Class.class));
        verify(mongoOperations, never()).save(any(Document.class), eq(MARKERS));
    }

    @Test
    void backfillsPositionsInBatchesAndWritesTheCompletionMarker() {
        var located = SecurityReportDocument.builder()
                .id("report-with-location")
                .location(LocationDocument.builder().latitude(10.391).longitude(-75.479).build())
                .build();
        when(mongoOperations.exists(any(Query.class), eq(MARKERS))).thenReturn(Mono.just(false));
        when(mongoOperations.updateMulti(any(Query.class), any(Update.class), any(Class.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        when(mongoOperations.find(any(Query.class), eq(SecurityReportDocument.class)))
                .thenReturn(Flux.just(located));
        when(mongoOperations.save(any(SecurityReportDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(mongoOperations.save(any(Document.class), eq(MARKERS)))
                .thenReturn(Mono.just(new Document()));

        migration(true).run(null);

        assertThat(located.getPosition()).isNotNull();
        verify(mongoOperations).save(located);
        verify(mongoOperations).save(any(Document.class), eq(MARKERS));
    }

    @Test
    void doesNotBlockTheBootWhenABackfillStepFails() {
        when(mongoOperations.exists(any(Query.class), eq(MARKERS)))
                .thenReturn(Mono.error(new IllegalStateException("mongo unavailable")));

        assertThatCode(() -> migration(true).run(null)).doesNotThrowAnyException();
    }

    private LegacyDataMigration migration(boolean enabled) {
        return new LegacyDataMigration(mongoOperations, new MigrationProperties(enabled, 100, Duration.ofSeconds(30)));
    }
}
