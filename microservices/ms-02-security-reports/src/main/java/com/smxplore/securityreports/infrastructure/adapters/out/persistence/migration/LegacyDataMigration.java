package com.smxplore.securityreports.infrastructure.adapters.out.persistence.migration;

import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Idempotent backfill for documents created before createdAt and optimistic
 * locking were introduced. It runs on startup and does nothing once migrated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyDataMigration implements ApplicationRunner {

    private final ReactiveMongoOperations mongoOperations;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        backfillCreatedAt(SecurityReportDocument.class, "security_reports");
        backfillCreatedAt(CommentDocument.class, "security_report_comments");
        backfillReportVersion();
    }

    private void backfillCreatedAt(Class<?> documentType, String collection) {
        var query = Query.query(Criteria.where("createdAt").is(null));
        var update = new Update().set("createdAt", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        var result = mongoOperations.updateMulti(query, update, documentType)
                .block(Duration.ofSeconds(30));

        if (result != null && result.getModifiedCount() > 0) {
            log.info("Backfilled createdAt for {} document(s) in collection '{}'.",
                    result.getModifiedCount(), collection);
        }
    }

    private void backfillReportVersion() {
        var query = Query.query(Criteria.where("version").is(null));
        var update = new Update().set("version", 0L);

        var result = mongoOperations.updateMulti(query, update, SecurityReportDocument.class)
                .block(Duration.ofSeconds(30));

        if (result != null && result.getModifiedCount() > 0) {
            log.info("Backfilled version for {} security report document(s).", result.getModifiedCount());
        }
    }
}
