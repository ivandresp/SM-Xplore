package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.AbstractNeo4jIntegrationTest;
import com.smxplore.recommendations.TestSeedData;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Neo4jRecommendationAdapterIntegrationTests extends AbstractNeo4jIntegrationTest {

    @Autowired
    private RecommendationGraphPort graphPort;

    @Autowired
    private Driver driver;

    @Test
    void affinityCandidatesExcludeReviewedAndExpiredItems() {
        List<RecommendationCandidate> candidates = graphPort
                .findCandidatesForTourist(TestSeedData.ANA, null, null, 50, Instant.now())
                .collectList()
                .block();

        assertThat(candidates).isNotNull();
        assertThat(candidates).isNotEmpty();
        assertThat(candidates)
                .extracting(RecommendationCandidate::itemId)
                .doesNotContain(TestSeedData.NOCHE_DE_JAZZ, TestSeedData.EVENTO_PASADO);
        assertThat(candidates)
                .allSatisfy(candidate -> {
                    assertThat(candidate.origin()).isEqualTo(CandidateOrigin.AFFINITY);
                    assertThat(candidate.affinity()).isPositive();
                    assertThat(candidate.itemType()).isNotNull();
                });
    }

    @Test
    void fallbackCandidatesAreAvailableForNewTourists() {
        List<RecommendationCandidate> candidates = graphPort
                .findFallbackCandidates(TestSeedData.NEW_TOURIST, null, null, 50, Instant.now())
                .collectList()
                .block();

        assertThat(candidates).isNotNull();
        assertThat(candidates).isNotEmpty();
        assertThat(candidates)
                .extracting(RecommendationCandidate::itemId)
                .doesNotContain(TestSeedData.EVENTO_PASADO);
        assertThat(candidates)
                .allSatisfy(candidate -> {
                    assertThat(candidate.origin()).isEqualTo(CandidateOrigin.FALLBACK);
                    assertThat(candidate.affinity()).isZero();
                });
    }

    @Test
    void createsAndRemovesPreference() {
        Boolean created = graphPort
                .upsertPreference(TestSeedData.ANA, TestSeedData.CULTURA, "es", Instant.now())
                .block();
        Boolean unknown = graphPort
                .upsertPreference(TestSeedData.ANA, UUID.randomUUID(), "es", Instant.now())
                .block();

        assertThat(created).isTrue();
        assertThat(unknown).isFalse();

        try (Session session = driver.session()) {
            long beforeDelete = session.run("""
                            MATCH (:TuristaProfile {touristId: $touristId})-[g:LE_GUSTA]->(:Categoria {categoriaId: $categoryId})
                            RETURN count(g) AS count
                            """, java.util.Map.of(
                            "touristId", TestSeedData.ANA.toString(),
                            "categoryId", TestSeedData.CULTURA.toString()))
                    .single()
                    .get("count")
                    .asLong();
            assertThat(beforeDelete).isEqualTo(1);
        }

        graphPort.deletePreference(TestSeedData.ANA, TestSeedData.CULTURA).block();

        try (Session session = driver.session()) {
            long afterDelete = session.run("""
                            MATCH (:TuristaProfile {touristId: $touristId})-[g:LE_GUSTA]->(:Categoria {categoriaId: $categoryId})
                            RETURN count(g) AS count
                            """, java.util.Map.of(
                            "touristId", TestSeedData.ANA.toString(),
                            "categoryId", TestSeedData.CULTURA.toString()))
                    .single()
                    .get("count")
                    .asLong();
            assertThat(afterDelete).isZero();
        }
    }

    @Test
    void recordsViewOnlyForExistingItems() {
        Boolean existing = graphPort
                .recordView(TestSeedData.ANA, ItemType.EVENT, TestSeedData.FESTIVAL_DEL_MAR, "es", Instant.now())
                .block();
        Boolean missing = graphPort
                .recordView(TestSeedData.ANA, ItemType.EVENT, UUID.randomUUID(), "es", Instant.now())
                .block();

        assertThat(existing).isTrue();
        assertThat(missing).isFalse();
    }

    @Test
    void viewsCreateInferredAffinity() {
        UUID tourist = UUID.randomUUID();

        Boolean recorded = graphPort
                .recordView(tourist, ItemType.EVENT, TestSeedData.FERIA_GASTRONOMICA, "es", Instant.now())
                .block();

        assertThat(recorded).isTrue();

        try (Session session = driver.session()) {
            long views = session.run("""
                            MATCH (:TuristaProfile {touristId: $touristId})-[:VIO]->(i:CatalogItem {itemId: $itemId})
                            RETURN count(*) AS count
                            """, java.util.Map.of("touristId", tourist.toString(),
                            "itemId", TestSeedData.FERIA_GASTRONOMICA.toString()))
                    .single()
                    .get("count")
                    .asLong();
            assertThat(views).isEqualTo(1);

            List<String> itemCategories = session.run("""
                            MATCH (i:CatalogItem {itemId: $itemId})-[:ES_DE_TIPO]->(c:Categoria)
                            RETURN c.categoriaId AS categoryId
                            """, java.util.Map.of("itemId", TestSeedData.FERIA_GASTRONOMICA.toString()))
                    .list(record -> record.get("categoryId").asString());
            assertThat(itemCategories).contains(TestSeedData.GASTRONOMIA.toString());

            List<String> preferences = session.run("""
                            MATCH (t:TuristaProfile {touristId: $touristId})-[g:LE_GUSTA]->(c:Categoria)
                            RETURN c.categoriaId AS categoryId, g.source AS source, g.weight AS weight
                            """, java.util.Map.of("touristId", tourist.toString()))
                    .list(record -> record.get("categoryId").asString());
            assertThat(preferences).contains(TestSeedData.GASTRONOMIA.toString());
        }

        List<RecommendationCandidate> candidates = graphPort
                .findCandidatesForTourist(tourist, null, null, 50, Instant.now())
                .collectList()
                .block();

        assertThat(candidates).isNotNull();
        assertThat(candidates).isNotEmpty();
        assertThat(candidates)
                .allSatisfy(candidate -> {
                    assertThat(candidate.origin()).isEqualTo(CandidateOrigin.AFFINITY);
                    assertThat(candidate.affinity()).isPositive();
                });
    }

    @Test
    void graphHasNoReservationEdges() {
        try (Session session = driver.session()) {
            long count = session.run("MATCH ()-[r:RESERVO]->() RETURN count(r) AS count")
                    .single()
                    .get("count")
                    .asLong();
            assertThat(count).isZero();
        }
    }
}
