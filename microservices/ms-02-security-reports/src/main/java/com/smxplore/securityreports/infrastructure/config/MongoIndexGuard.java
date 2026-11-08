package com.smxplore.securityreports.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MS02-17: when Spring Data auto-index-creation is off, nothing creates the indexes the
 * queries depend on (the {@code nearby} query needs the 2dsphere index on {@code position}).
 * This guard verifies them at startup: outside {@code local}/{@code dev}/{@code test} a
 * missing index fails the boot with the exact names and where the DDL lives.
 *
 * <p>DECISION: production keeps {@code MONGO_AUTO_INDEX_CREATION=true} (see
 * {@code deploy/persistence/INDICES.md}); the guard covers deployments that turn it off
 * and rely on a provisioning job.</p>
 */
@Slf4j
@Component
public class MongoIndexGuard implements ApplicationRunner {

    static final String INDICES_DOCUMENT = "deploy/persistence/INDICES.md";

    static final Map<String, List<String>> REQUIRED_INDEXES = new LinkedHashMap<>(Map.of(
            "security_reports", List.of(
                    "idx_security_reports_status_created_at_v2",
                    "idx_security_reports_user_created_at_v2",
                    "idx_security_reports_position"),
            "security_report_comments", List.of(
                    "idx_security_report_comments_report_created_at_v2")));

    private final ReactiveMongoOperations mongoOperations;
    private final Environment environment;
    private final boolean autoIndexCreation;

    public MongoIndexGuard(ReactiveMongoOperations mongoOperations,
                           Environment environment,
                           @Value("${spring.data.mongodb.auto-index-creation:false}") boolean autoIndexCreation) {
        this.mongoOperations = mongoOperations;
        this.environment = environment;
        this.autoIndexCreation = autoIndexCreation;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (autoIndexCreation) {
            log.debug("MongoDB indexes are created by Spring Data (auto-index-creation=true); guard skipped.");
            return;
        }

        var missing = new ArrayList<String>();
        REQUIRED_INDEXES.forEach((collection, expected) -> {
            var existing = existingIndexNames(collection);
            expected.stream()
                    .filter(name -> !existing.contains(name))
                    .forEach(name -> missing.add(collection + "." + name));
        });

        if (missing.isEmpty()) {
            log.info("MongoDB indexes verified ({} collections).", REQUIRED_INDEXES.size());
            return;
        }

        var message = "MongoDB indexes missing: " + missing
                + ". Provision them with the DDL in " + INDICES_DOCUMENT
                + " or set MONGO_AUTO_INDEX_CREATION=true.";

        if (environment.acceptsProfiles(Profiles.of("local", "dev", "test"))) {
            log.warn("{} (relaxed profile: the service still starts)", message);
            return;
        }
        throw new IllegalStateException(message);
    }

    private Set<String> existingIndexNames(String collection) {
        var names = mongoOperations.indexOps(collection)
                .getIndexInfo()
                .map(IndexInfo::getName)
                .collectList()
                .block(Duration.ofSeconds(10));
        return names == null ? Set.of() : Set.copyOf(names);
    }
}
