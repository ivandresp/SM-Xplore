package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.domain.model.CandidateOrigin;
import com.smxplore.recommendations.domain.model.ItemType;
import com.smxplore.recommendations.domain.model.RecommendationCandidate;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import java.time.Instant;
import java.util.UUID;

final class Neo4jRecordMapper {

    private Neo4jRecordMapper() {
    }

    static RecommendationCandidate mapCandidate(Record record, CandidateOrigin origin) {
        return new RecommendationCandidate(
                ItemType.valueOf(string(record, "itemType")),
                uuid(record, "itemId"),
                string(record, "name"),
                uuid(record, "categoryId"),
                string(record, "categoryName"),
                uuid(record, "siteId"),
                nullableDouble(record, "latitude"),
                nullableDouble(record, "longitude"),
                nullableInstant(record, "startsAt"),
                nullableInstant(record, "endsAt"),
                nullableInstant(record, "updatedAt"),
                doubleValue(record, "ratingAvg", 0.0),
                longValue(record, "ratingCount", 0L),
                doubleValue(record, "affinity", 0.0),
                origin
        );
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

    private static double doubleValue(Record record, String key, double defaultValue) {
        Value value = record.get(key);
        return value == null || value.isNull() ? defaultValue : value.asDouble();
    }

    private static long longValue(Record record, String key, long defaultValue) {
        Value value = record.get(key);
        return value == null || value.isNull() ? defaultValue : value.asLong();
    }

    private static Instant nullableInstant(Record record, String key) {
        Value value = record.get(key);
        return value == null || value.isNull() ? null : value.asZonedDateTime().toInstant();
    }
}
