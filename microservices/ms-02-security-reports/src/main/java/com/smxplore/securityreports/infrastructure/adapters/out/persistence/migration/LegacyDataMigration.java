package com.smxplore.securityreports.infrastructure.adapters.out.persistence.migration;

import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import com.smxplore.securityreports.infrastructure.config.MigrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Idempotent backfill for documents created before createdAt, optimistic locking and the
 * GeoJSON position existed.
 *
 * <p>MS02-16: it processes positions in bounded batches (never loading the whole collection),
 * runs inside a total time budget and writes a completion marker in
 * {@code security_report_migrations} so a finished backfill is not repeated on every start.
 * If the budget is exhausted the marker is not written: the backfill is idempotent and resumes
 * on the next start instead of blocking the service.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyDataMigration implements ApplicationRunner {

    static final String MARKER_COLLECTION = "security_report_migrations";
    static final String MARKER_ID = "legacy-backfill-v1";

    private final ReactiveMongoOperations mongoOperations;
    private final MigrationProperties properties;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!properties.enabled()) {
            log.debug("Legacy backfill disabled (app.migration.enabled=false).");
            return;
        }

        var deadline = Instant.now().plus(properties.timeout());
        try {
            if (isAlreadyCompleted(deadline)) {
                log.info("Legacy backfill already completed (marker '{}'), skipping.", MARKER_ID);
                return;
            }

            backfillCreatedAt(SecurityReportDocument.class, "security_reports", deadline);
            backfillCreatedAt(CommentDocument.class, "security_report_comments", deadline);
            backfillReportVersion(deadline);
            backfillUpdatedAt(deadline);
            backfillPositions(deadline);
            markCompleted(deadline);

            log.info("Legacy backfill finished (batch-size={}, timeout={}).",
                    properties.batchSize(), properties.timeout());
        } catch (RuntimeException exception) {
            // Requested by MS02-16: a slow migration must not block the boot; it resumes next time.
            log.error("Legacy backfill did not finish within its budget: {}. "
                    + "It is idempotent and will resume on the next start.", exception.getMessage());
        }
    }

    private boolean isAlreadyCompleted(Instant deadline) {
        return Boolean.TRUE.equals(mongoOperations
                .exists(Query.query(Criteria.where("_id").is(MARKER_ID)), MARKER_COLLECTION)
                .block(remaining(deadline)));
    }

    private void backfillCreatedAt(Class<?> documentType, String collection, Instant deadline) {
        var query = Query.query(Criteria.where("createdAt").is(null));
        var update = new Update().set("createdAt", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        var result = mongoOperations.updateMulti(query, update, documentType)
                .block(remaining(deadline));

        if (result != null && result.getModifiedCount() > 0) {
            log.info("Backfilled createdAt for {} document(s) in collection '{}'.",
                    result.getModifiedCount(), collection);
        }
    }

    private void backfillReportVersion(Instant deadline) {
        var query = Query.query(Criteria.where("version").is(null));
        var update = new Update().set("version", 0L);

        var result = mongoOperations.updateMulti(query, update, SecurityReportDocument.class)
                .block(remaining(deadline));

        if (result != null && result.getModifiedCount() > 0) {
            log.info("Backfilled version for {} security report document(s).", result.getModifiedCount());
        }
    }

    private void backfillUpdatedAt(Instant deadline) {
        var query = Query.query(Criteria.where("updatedAt").is(null));
        var update = new Update().set("updatedAt", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        var result = mongoOperations.updateMulti(query, update, SecurityReportDocument.class)
                .block(remaining(deadline));

        if (result != null && result.getModifiedCount() > 0) {
            log.info("Backfilled updatedAt for {} security report document(s).", result.getModifiedCount());
        }
    }

    private void backfillPositions(Instant deadline) {
        var total = 0L;
        long updated;
        do {
            updated = backfillPositionBatch(deadline);
            total += updated;
        } while (updated == properties.batchSize());

        if (total > 0) {
            log.info("Backfilled geo position for {} security report document(s) in batches of {}.",
                    total, properties.batchSize());
        }
    }

    private long backfillPositionBatch(Instant deadline) {
        var query = Query.query(Criteria.where("position").is(null)
                        .and("location").ne(null)
                        .and("location.latitude").ne(null)
                        .and("location.longitude").ne(null))
                .limit(properties.batchSize());

        var updated = mongoOperations.find(query, SecurityReportDocument.class)
                .concatMap(document -> {
                    var location = document.getLocation();
                    document.setPosition(new GeoJsonPoint(location.getLongitude(), location.getLatitude()));
                    return mongoOperations.save(document);
                })
                .count()
                .block(remaining(deadline));

        return updated == null ? 0L : updated;
    }

    private void markCompleted(Instant deadline) {
        var marker = new Document("_id", MARKER_ID)
                .append("status", "COMPLETED")
                .append("completedAt", Instant.now().truncatedTo(ChronoUnit.MILLIS));
        mongoOperations.save(marker, MARKER_COLLECTION).block(remaining(deadline));
    }

    private static Duration remaining(Instant deadline) {
        var left = Duration.between(Instant.now(), deadline);
        if (left.isNegative() || left.isZero()) {
            throw new IllegalStateException("backfill time budget exhausted");
        }
        return left;
    }
}
