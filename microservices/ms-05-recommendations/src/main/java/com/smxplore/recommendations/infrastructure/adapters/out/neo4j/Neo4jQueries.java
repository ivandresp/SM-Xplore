package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

public record Neo4jQueries() {

    static String affinityCandidates(int categoryMaxDepth) {
        if (categoryMaxDepth < 0 || categoryMaxDepth > 5) {
            throw new IllegalArgumentException("categoryMaxDepth must be between 0 and 5");
        }
        return """
            MATCH (t:TuristaProfile {touristId: $touristId})-[g:LE_GUSTA]->(liked:Categoria)
            MATCH (liked)<-[:PERTENECE_A*0..%d]-(c:Categoria)<-[:ES_DE_TIPO]-(item:CatalogItem)
            WHERE item.status = 'PUBLISHED'
              AND (item.endsAt IS NULL OR item.endsAt > datetime($now))
              AND ($siteId IS NULL OR item.siteId = $siteId)
              AND NOT EXISTS { (t)-[review:`RESEÑO`]->(item)
                               WHERE coalesce(review.status, 'PUBLISHED') = 'PUBLISHED' }
            WITH item,
                 sum(coalesce(g.weight, 1.0)
                     * 0.5 ^ (duration.inDays(coalesce(g.updatedAt, datetime($now)), datetime($now)).days
                              / $preferenceHalfLifeDays))
                     AS affinity,
                 collect(DISTINCT c.name) AS categoryNames
            RETURN item.itemType AS itemType,
                   item.itemId AS itemId,
                   item.name AS name,
                   item.categoryId AS categoryId,
                   head(categoryNames) AS categoryName,
                   item.siteId AS siteId,
                   item.latitude AS latitude,
                   item.longitude AS longitude,
                   item.startsAt AS startsAt,
                   item.endsAt AS endsAt,
                   item.updatedAt AS updatedAt,
                   coalesce(item.ratingAvg, 0.0) AS ratingAvg,
                   coalesce(item.ratingCount, 0) AS ratingCount,
                   affinity
            ORDER BY affinity DESC, item.itemId ASC
            LIMIT $candidateLimit
            """.formatted(categoryMaxDepth);
    }

    static final String FALLBACK_CANDIDATES = """
            MATCH (item:CatalogItem)
            WHERE item.status = 'PUBLISHED'
              AND (item.endsAt IS NULL OR item.endsAt > datetime($now))
              AND ($siteId IS NULL OR item.siteId = $siteId)
              AND NOT EXISTS { (:TuristaProfile {touristId: $touristId})-[review:`RESEÑO`]->(item)
                               WHERE coalesce(review.status, 'PUBLISHED') = 'PUBLISHED' }
            OPTIONAL MATCH (item)-[:ES_DE_TIPO]->(c:Categoria)
            WITH item, collect(DISTINCT c.name) AS categoryNames
            RETURN item.itemType AS itemType,
                   item.itemId AS itemId,
                   item.name AS name,
                   item.categoryId AS categoryId,
                   head(categoryNames) AS categoryName,
                   item.siteId AS siteId,
                   item.latitude AS latitude,
                   item.longitude AS longitude,
                   item.startsAt AS startsAt,
                   item.endsAt AS endsAt,
                   item.updatedAt AS updatedAt,
                   coalesce(item.ratingAvg, 0.0) AS ratingAvg,
                   coalesce(item.ratingCount, 0) AS ratingCount,
                   0.0 AS affinity
            ORDER BY coalesce(item.ratingAvg, 0.0) DESC, item.updatedAt DESC
            LIMIT $candidateLimit
            """;

    static final String UPSERT_PREFERENCE = """
            MERGE (t:TuristaProfile {touristId: $touristId})
            ON CREATE SET t.language = $language, t.updatedAt = datetime($now)
            ON MATCH SET t.language = coalesce($language, t.language), t.updatedAt = datetime($now)
            WITH t
            MATCH (c:Categoria {categoriaId: $categoryId})
            MERGE (t)-[g:LE_GUSTA]->(c)
            SET g.weight = 1.0, g.source = 'EXPLICIT', g.updatedAt = datetime($now)
            RETURN c.categoriaId AS categoryId
            """;

    static final String DELETE_PREFERENCE = """
            MATCH (t:TuristaProfile {touristId: $touristId})-[g:LE_GUSTA]->(c:Categoria {categoriaId: $categoryId})
            DELETE g
            """;

    static final String RECORD_VIEW = """
            MERGE (t:TuristaProfile {touristId: $touristId})
            ON CREATE SET t.language = $language, t.updatedAt = datetime($now)
            ON MATCH SET t.language = coalesce($language, t.language), t.updatedAt = datetime($now)
            WITH t
            MATCH (item:CatalogItem {itemId: $itemId, itemType: $itemType})
            MERGE (t)-[v:VIO]->(item)
            ON CREATE SET v.firstAt = datetime($now), v.count = 0
            SET v.lastAt = datetime($now), v.count = coalesce(v.count, 0) + 1
            WITH t, item
            OPTIONAL MATCH (item)-[:ES_DE_TIPO]->(c:Categoria)
            FOREACH (_ IN CASE WHEN c IS NULL THEN [] ELSE [1] END |
                MERGE (t)-[g:LE_GUSTA]->(c)
                ON CREATE SET g.weight = 0.15, g.source = 'INFERRED', g.updatedAt = datetime($now)
                ON MATCH SET g.weight = CASE WHEN g.source = 'EXPLICIT' THEN g.weight ELSE 0.15 END,
                              g.updatedAt = datetime($now)
            )
            RETURN item.itemId AS itemId
            """;
}
