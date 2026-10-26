package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import java.time.Instant;
import java.util.UUID;

final class Neo4jRecordMapper {

    private Neo4jRecordMapper() {
    }

    static RecommendationCandidate mapCandidate(Record record, CandidateOrigin origin) {
        RecommendationCandidate candidate = RecommendationCandidate.builder()
                .itemType(ItemType.valueOf(string(record, "itemType")))
                .itemId(uuid(record, "itemId"))
                .name(string(record, "name"))
                .categoryId(uuid(record, "categoryId"))
                .categoryName(string(record, "categoryName"))
                .siteId(uuid(record, "siteId"))
                .latitude(nullableDouble(record, "latitude"))
                .longitude(nullableDouble(record, "longitude"))
                .startsAt(nullableInstant(record, "startsAt"))
                .endsAt(nullableInstant(record, "endsAt"))
                .updatedAt(nullableInstant(record, "updatedAt"))
                .ratingAvg(doubleValue(record, "ratingAvg"))
                .ratingCount(longValue(record))
                .affinity(doubleValue(record, "affinity"))
                .origin(origin)
                .build();
        candidate.validate();
        return candidate;
    }

    private static String string(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static UUID uuid(Record record, String key) {
        String value = string(record, key);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static Double nullableDouble(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private static double doubleValue(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? 0.0 : value.asDouble();
    }

    private static long longValue(Record record) {
        Value value = record.get("ratingCount");
        return value == null || value.isNull() ? 0L : value.asLong();
    }

    private static Instant nullableInstant(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asZonedDateTime().toInstant();
    }
}
