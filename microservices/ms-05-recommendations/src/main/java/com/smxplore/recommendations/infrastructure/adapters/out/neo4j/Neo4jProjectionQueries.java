package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

public record Neo4jProjectionQueries() {

    static final String PROCESSED_EXISTS = """
            MATCH (p:ProcessedEvent {eventId: $eventId})
            RETURN p.eventId AS eventId
            """;

    static final String MARK_PROCESSED = """
            MERGE (p:ProcessedEvent {eventId: $eventId})
            ON CREATE SET p.eventType = $eventType,
                          p.aggregateId = $aggregateId,
                          p.occurredAt = datetime($occurredAt),
                          p.processedAt = datetime(),
                          p.outcome = $outcome
            """;

    static final String STALE_CATEGORY = """
            MATCH (c:Categoria {categoriaId: $id})
            RETURN c.updatedAt IS NOT NULL AND c.updatedAt >= datetime($updatedAt) AS stale
            """;

    static final String STALE_ITEM = """
            MATCH (i:CatalogItem {itemId: $id})
            RETURN i.updatedAt IS NOT NULL AND i.updatedAt >= datetime($updatedAt) AS stale
            """;

    static final String STALE_REVIEW = """
            MATCH (:TuristaProfile {touristId: $touristId})
                  -[r:`RESEÑO` {reviewId: $reviewId}]->
                  (:CatalogItem {itemId: $itemId})
            RETURN r.updatedAt IS NOT NULL AND r.updatedAt >= datetime($updatedAt) AS stale
            """;

    static final String UPSERT_CATEGORY = """
            MERGE (c:Categoria {categoriaId: $categoryId})
            SET c.name = $name,
                c.scope = $scope,
                c.parentId = $parentId,
                c.updatedAt = datetime($updatedAt)
            WITH c
            OPTIONAL MATCH (c)-[oldParent:PERTENECE_A]->(:Categoria)
            FOREACH (rel IN CASE WHEN oldParent IS NULL THEN [] ELSE [oldParent] END | DELETE rel)
            WITH c
            OPTIONAL MATCH (parent:Categoria {categoriaId: $parentId})
            FOREACH (_ IN CASE WHEN parent IS NULL THEN [] ELSE [1] END |
                MERGE (c)-[:PERTENECE_A]->(parent)
            )
            WITH c
            OPTIONAL MATCH (child:Categoria {parentId: $categoryId})
            WITH c, collect(child) AS children
            FOREACH (childNode IN children | MERGE (childNode)-[:PERTENECE_A]->(c))
            WITH c
            OPTIONAL MATCH (item:CatalogItem {categoryId: $categoryId})
            WITH c, collect(item) AS items
            FOREACH (itemNode IN items | MERGE (itemNode)-[:ES_DE_TIPO]->(c))
            """;

    static final String UPSERT_SITE = """
            MERGE (s:CatalogItem {itemId: $siteId})
            SET s:Sitio,
                s.itemId = $siteId,
                s.itemType = 'SITE',
                s.siteId = $siteId,
                s.name = $name,
                s.categoryId = $categoryId,
                s.latitude = $latitude,
                s.longitude = $longitude,
                s.location = point({latitude: $latitude, longitude: $longitude}),
                s.status = $status,
                s.updatedAt = datetime($updatedAt)
            WITH s
            OPTIONAL MATCH (s)-[oldCategory:ES_DE_TIPO]->(:Categoria)
            FOREACH (rel IN CASE WHEN oldCategory IS NULL THEN [] ELSE [oldCategory] END | DELETE rel)
            WITH s
            OPTIONAL MATCH (category:Categoria {categoriaId: $categoryId})
            FOREACH (_ IN CASE WHEN category IS NULL THEN [] ELSE [1] END |
                MERGE (s)-[:ES_DE_TIPO]->(category)
            )
            WITH s
            OPTIONAL MATCH (child:CatalogItem)
            WHERE child.siteId = $siteId
              AND child.itemId <> $siteId
              AND child.itemType IN ['ACTIVITY', 'EVENT']
            WITH s, collect(child) AS children
            FOREACH (childNode IN children | MERGE (childNode)-[:OCURRE_EN]->(s))
            WITH s
            OPTIONAL MATCH (service:CatalogItem:Servicio)
            WHERE service.siteId = $siteId
            WITH s, collect(service) AS services
            FOREACH (serviceNode IN services | MERGE (serviceNode)-[:UBICADO_EN]->(s))
            """;

    static final String UPSERT_SERVICE = """
            MERGE (v:CatalogItem {itemId: $serviceId})
            SET v:Servicio,
                v.itemId = $serviceId,
                v.itemType = 'SERVICE',
                v.serviceId = $serviceId,
                v.siteId = $siteId,
                v.name = $name,
                v.type = $type,
                v.categoryId = $categoryId,
                v.latitude = $latitude,
                v.longitude = $longitude,
                v.location = point({latitude: $latitude, longitude: $longitude}),
                v.status = $status,
                v.updatedAt = datetime($updatedAt)
            WITH v
            OPTIONAL MATCH (v)-[oldCategory:ES_DE_TIPO]->(:Categoria)
            FOREACH (rel IN CASE WHEN oldCategory IS NULL THEN [] ELSE [oldCategory] END | DELETE rel)
            WITH v
            OPTIONAL MATCH (category:Categoria {categoriaId: $categoryId})
            FOREACH (_ IN CASE WHEN category IS NULL THEN [] ELSE [1] END |
                MERGE (v)-[:ES_DE_TIPO]->(category)
            )
            WITH v
            OPTIONAL MATCH (v)-[oldSite:UBICADO_EN]->(:Sitio)
            FOREACH (rel IN CASE WHEN oldSite IS NULL THEN [] ELSE [oldSite] END | DELETE rel)
            WITH v
            OPTIONAL MATCH (site:Sitio {siteId: $siteId})
            FOREACH (_ IN CASE WHEN site IS NULL THEN [] ELSE [1] END |
                MERGE (v)-[:UBICADO_EN]->(site)
            )
            """;

    static final String UPSERT_ACTIVITY = """
            MERGE (a:CatalogItem {itemId: $activityId})
            SET a:Actividad,
                a.itemId = $activityId,
                a.itemType = 'ACTIVITY',
                a.activityId = $activityId,
                a.name = $name,
                a.categoryId = $categoryId,
                a.siteId = $siteId,
                a.latitude = $latitude,
                a.longitude = $longitude,
                a.price = $price,
                a.status = $status,
                a.updatedAt = datetime($updatedAt)
            WITH a
            OPTIONAL MATCH (a)-[oldCategory:ES_DE_TIPO]->(:Categoria)
            FOREACH (rel IN CASE WHEN oldCategory IS NULL THEN [] ELSE [oldCategory] END | DELETE rel)
            WITH a
            OPTIONAL MATCH (category:Categoria {categoriaId: $categoryId})
            FOREACH (_ IN CASE WHEN category IS NULL THEN [] ELSE [1] END |
                MERGE (a)-[:ES_DE_TIPO]->(category)
            )
            WITH a
            OPTIONAL MATCH (a)-[oldSite:OCURRE_EN]->(:Sitio)
            FOREACH (rel IN CASE WHEN oldSite IS NULL THEN [] ELSE [oldSite] END | DELETE rel)
            WITH a
            OPTIONAL MATCH (site:Sitio {siteId: $siteId})
            FOREACH (_ IN CASE WHEN site IS NULL THEN [] ELSE [1] END |
                MERGE (a)-[:OCURRE_EN]->(site)
            )
            """;

    static final String UPSERT_EVENT = """
            MERGE (e:CatalogItem {itemId: $eventId})
            SET e:Evento,
                e.itemId = $eventId,
                e.itemType = 'EVENT',
                e.eventId = $eventId,
                e.name = $name,
                e.categoryId = $categoryId,
                e.siteId = $siteId,
                e.latitude = $latitude,
                e.longitude = $longitude,
                e.price = $price,
                e.startsAt = datetime($startsAt),
                e.endsAt = CASE WHEN $endsAt IS NULL THEN null ELSE datetime($endsAt) END,
                e.status = $status,
                e.updatedAt = datetime($updatedAt)
            WITH e
            OPTIONAL MATCH (e)-[oldCategory:ES_DE_TIPO]->(:Categoria)
            FOREACH (rel IN CASE WHEN oldCategory IS NULL THEN [] ELSE [oldCategory] END | DELETE rel)
            WITH e
            OPTIONAL MATCH (category:Categoria {categoriaId: $categoryId})
            FOREACH (_ IN CASE WHEN category IS NULL THEN [] ELSE [1] END |
                MERGE (e)-[:ES_DE_TIPO]->(category)
            )
            WITH e
            OPTIONAL MATCH (e)-[oldSite:OCURRE_EN]->(:Sitio)
            FOREACH (rel IN CASE WHEN oldSite IS NULL THEN [] ELSE [oldSite] END | DELETE rel)
            WITH e
            OPTIONAL MATCH (site:Sitio {siteId: $siteId})
            FOREACH (_ IN CASE WHEN site IS NULL THEN [] ELSE [1] END |
                MERGE (e)-[:OCURRE_EN]->(site)
            )
            """;

    static final String UPSERT_REVIEW = """
            MERGE (t:TuristaProfile {touristId: $touristId})
            ON CREATE SET t.updatedAt = datetime($updatedAt)
            WITH t
            MERGE (i:CatalogItem {itemId: $itemId})
            ON CREATE SET i.itemType = $itemType
            WITH t, i
            MERGE (t)-[r:`RESEÑO` {reviewId: $reviewId}]->(i)
            SET r.rating = $rating,
                r.status = $status,
                r.itemType = $itemType,
                r.itemId = $itemId,
                r.at = datetime($updatedAt),
                r.updatedAt = datetime($updatedAt)
            WITH i
            OPTIONAL MATCH (i)<-[review:`RESEÑO`]-(:TuristaProfile)
            WHERE coalesce(review.status, 'PUBLISHED') = 'PUBLISHED'
            WITH i,
                 count(review) AS ratingCount,
                 coalesce(avg(review.rating), 0.0) AS ratingAvg,
                 max(review.at) AS lastReviewAt
            SET i.ratingCount = ratingCount,
                i.ratingAvg = ratingAvg,
                i.lastReviewAt = lastReviewAt
            """;
}
