package com.smxplore.securityreports.infrastructure.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS02-17: si Spring Data no crea los indices, el arranque verifica los que necesitan las
 * consultas y falla fuera de local/dev/test cuando falta alguno.
 */
class MongoIndexGuardTests {

    private final ReactiveMongoOperations operations = mock(ReactiveMongoOperations.class);

    @Test
    void skipsTheCheckWhenSpringDataCreatesTheIndexes() {
        guard(environment("prod"), true).run(null);

        verifyNoInteractions(operations);
    }

    @Test
    void startsWhenAllRequiredIndexesExist() {
        stubIndexes(Map.of(
                "security_reports", List.of(
                        "idx_security_reports_status_created_at_v2",
                        "idx_security_reports_user_created_at_v2",
                        "idx_security_reports_position"),
                "security_report_comments", List.of("idx_security_report_comments_report_created_at_v2")));

        assertThatCode(() -> guard(environment("prod"), false).run(null)).doesNotThrowAnyException();
    }

    @Test
    void onlyWarnsWhenIndexesAreMissingInRelaxedProfiles() {
        stubIndexes(Map.of("security_reports", List.of(), "security_report_comments", List.of()));

        assertThatCode(() -> guard(environment("local"), false).run(null)).doesNotThrowAnyException();
    }

    @Test
    void failsTheBootOutsideRelaxedProfilesWhenAnIndexIsMissing() {
        stubIndexes(Map.of(
                "security_reports", List.of(
                        "idx_security_reports_status_created_at_v2",
                        "idx_security_reports_user_created_at_v2"),
                "security_report_comments", List.of("idx_security_report_comments_report_created_at_v2")));

        assertThatThrownBy(() -> guard(environment("prod"), false).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idx_security_reports_position")
                .hasMessageContaining("INDICES.md");
    }

    private MongoIndexGuard guard(MockEnvironment environment, boolean autoIndexCreation) {
        return new MongoIndexGuard(operations, environment, autoIndexCreation);
    }

    private void stubIndexes(Map<String, List<String>> namesByCollection) {
        namesByCollection.forEach((collection, names) -> {
            var indexOperations = mock(ReactiveIndexOperations.class);
            when(indexOperations.getIndexInfo())
                    .thenReturn(Flux.fromIterable(names.stream().map(MongoIndexGuardTests::indexInfo).toList()));
            when(operations.indexOps(collection)).thenReturn(indexOperations);
        });
    }

    private static IndexInfo indexInfo(String name) {
        return IndexInfo.indexInfoOf(new Document("name", name)
                .append("key", new Document("_id", 1)));
    }

    private static MockEnvironment environment(String profile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
