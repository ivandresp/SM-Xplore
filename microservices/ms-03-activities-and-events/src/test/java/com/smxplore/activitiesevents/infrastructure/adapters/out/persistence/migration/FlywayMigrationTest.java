package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant STARTS_AT = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    void resetSchema() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void freshDatabaseGetsTheSymbolicSchema() throws Exception {
        flyway().migrate();

        try (var connection = connection()) {
            assertThat(columnExists(connection, "reservations", "item_type")).isTrue();
            assertThat(columnExists(connection, "reservations", "item_id")).isTrue();
            assertThat(columnExists(connection, "reservations", "item_starts_at")).isTrue();
            assertThat(columnExists(connection, "reservations", "unit_price")).isTrue();
            assertThat(columnExists(connection, "reservations", "idempotency_key")).isTrue();
            assertThat(tableExists(connection, "reservation_items")).isFalse();
            assertThat(tableExists(connection, "reservations_legacy")).isTrue();
            assertThat(tableExists(connection, "reservations_quarantine")).isTrue();
            assertThat(tableExists(connection, "reservation_items_legacy")).isTrue();
        }
        assertThat(flyway().info().current().getVersion())
                .isGreaterThanOrEqualTo(MigrationVersion.fromVersion("2"));
    }

    @Test
    void legacySingleItemReservationIsMigratedAndArchived() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        try (var connection = connection()) {
            insertActivity(connection, activityId);
            insertReservation(connection, reservationId, 2, "200.00", "PENDING", STARTS_AT.plusSeconds(3600));
            insertItem(connection, reservationId, activityId, "ACTIVITY", "100.00");
        }

        flyway().migrate();

        try (var connection = connection()) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT status, item_type, unit_price, total, item_starts_at, booked_for
                         FROM reservations WHERE id = '%s'
                         """.formatted(reservationId))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("CONFIRMED");
                assertThat(result.getString("item_type")).isEqualTo("ACTIVITY");
                assertThat(result.getBigDecimal("unit_price")).isEqualByComparingTo("100.00");
                assertThat(result.getBigDecimal("total")).isEqualByComparingTo("200.00");
                assertThat(result.getTimestamp("item_starts_at").toInstant()).isEqualTo(STARTS_AT);
                assertThat(result.getTimestamp("booked_for").toInstant()).isEqualTo(STARTS_AT);
            }
            assertThat(count(connection, "reservations")).isEqualTo(1);
            assertThat(count(connection, "reservations_legacy")).isEqualTo(1);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT status, total FROM reservations_legacy WHERE id = '%s'
                         """.formatted(reservationId))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("PENDING");
                assertThat(result.getBigDecimal("total")).isEqualByComparingTo("200.00");
            }
            assertThat(count(connection, "reservation_items_legacy")).isEqualTo(1);
            assertThat(count(connection, "reservations_quarantine")).isZero();
            assertThat(tableExists(connection, "reservation_items")).isFalse();
        }
    }

    @Test
    void pendingReservationsOverCapacityAreQuarantined() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        try (var connection = connection()) {
            insertActivity(connection, activityId, 1, STARTS_AT);
            insertReservation(connection, first, 1, "100.00", "PENDING", STARTS_AT);
            insertItem(connection, first, activityId, "ACTIVITY", "100.00");
            insertReservation(connection, second, 1, "100.00", "PENDING", STARTS_AT);
            insertItem(connection, second, activityId, "ACTIVITY", "100.00");
        }

        flyway().migrate();

        try (var connection = connection()) {
            assertThat(countWhere(connection, "reservations", "status = 'CONFIRMED'")).isEqualTo(1);
            assertThat(countWhere(connection, "reservations_quarantine",
                    "reason = 'pending over capacity'")).isEqualTo(1);
        }
    }

    @Test
    void nonDivisibleTotalIsQuarantined() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        try (var connection = connection()) {
            insertActivity(connection, activityId, 10, STARTS_AT);
            insertReservation(connection, reservationId, 3, "100.00", "CONFIRMED", STARTS_AT);
            insertItem(connection, reservationId, activityId, "ACTIVITY", "100.00");
        }

        flyway().migrate();

        try (var connection = connection()) {
            assertThat(count(connection, "reservations")).isZero();
            assertThat(countWhere(connection, "reservations_quarantine",
                    "reason = 'non-divisible total'")).isEqualTo(1);
        }
    }

    @Test
    void reservationCreatedAfterTheItemStartedIsQuarantined() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        try (var connection = connection()) {
            insertActivity(connection, activityId, 10, Instant.parse("2026-01-01T07:00:00Z"));
            insertReservation(connection, reservationId, 1, "100.00", "CONFIRMED", STARTS_AT);
            insertItem(connection, reservationId, activityId, "ACTIVITY", "100.00");
        }

        flyway().migrate();

        try (var connection = connection()) {
            assertThat(count(connection, "reservations")).isZero();
            assertThat(countWhere(connection, "reservations_quarantine",
                    "reason = 'start before booking'")).isEqualTo(1);
        }
    }

    @Test
    void multiItemOrphanAndMissingCatalogReservationsAreQuarantined() throws Exception {
        flywayAtV1().migrate();

        var activityA = UUID.randomUUID();
        var activityB = UUID.randomUUID();
        var multiId = UUID.randomUUID();
        var orphanId = UUID.randomUUID();
        var missingCatalogId = UUID.randomUUID();
        try (var connection = connection()) {
            insertActivity(connection, activityA);
            insertActivity(connection, activityB);

            insertReservation(connection, multiId, 2, "300.00", "CONFIRMED", STARTS_AT);
            insertItem(connection, multiId, activityA, "ACTIVITY", "100.00");
            insertItem(connection, multiId, activityA, "ACTIVITY", "100.00");
            insertItem(connection, multiId, activityB, "ACTIVITY", "200.00");

            insertReservation(connection, orphanId, 1, "50.00", "PENDING", STARTS_AT);

            insertReservation(connection, missingCatalogId, 1, "70.00", "CONFIRMED", STARTS_AT);
            insertItem(connection, missingCatalogId, UUID.randomUUID(), "ACTIVITY", "70.00");
        }

        flyway().migrate();

        try (var connection = connection()) {
            assertThat(count(connection, "reservations")).isZero();
            assertThat(count(connection, "reservations_legacy")).isEqualTo(3);
            assertThat(count(connection, "reservation_items_legacy")).isEqualTo(4);
            assertThat(count(connection, "reservations_quarantine")).isEqualTo(3);
            assertThat(countWhere(connection, "reservations_quarantine", "reason = 'multiple items'")).isEqualTo(1);
            assertThat(countWhere(connection, "reservations_quarantine", "reason = 'no item'")).isEqualTo(1);
            assertThat(countWhere(connection, "reservations_quarantine", "reason = 'activity not found'")).isEqualTo(1);
        }
    }

    @Test
    void existingLegacySchemaIsBaselinedAndMigrated() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        try (var connection = connection()) {
            insertActivity(connection, activityId);
            insertReservation(connection, reservationId, 2, "200.00", "PENDING", STARTS_AT.plusSeconds(3600));
            insertItem(connection, reservationId, activityId, "ACTIVITY", "100.00");
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE flyway_schema_history");
            }
        }

        flyway().migrate();

        try (var connection = connection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT version, type FROM flyway_schema_history ORDER BY installed_rank")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("0");
                assertThat(result.getString("type")).isEqualTo("BASELINE");
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("1");
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("2");
            }
            try (var result = statement.executeQuery(
                    "SELECT status, item_type FROM reservations WHERE id = '%s'".formatted(reservationId))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("CONFIRMED");
                assertThat(result.getString("item_type")).isEqualTo("ACTIVITY");
            }
            assertThat(count(connection, "reservations_legacy")).isEqualTo(1);
        }
    }

    @Test
    void alreadySymbolicDatabaseIsNotQuarantinedAgain() throws Exception {
        flyway().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        try (var connection = connection(); var statement = connection.createStatement()) {
            insertActivity(connection, activityId);
            statement.execute("""
                    INSERT INTO reservations (id, tourist_id, item_type, item_id, item_starts_at, unit_price,
                                              number_of_people, total, payment_method, status, booked_at, booked_for)
                    VALUES ('%s', '%s', 'ACTIVITY', '%s', '%s', 100.00, 2, 200.00, 'CASH', 'CONFIRMED',
                            '%s', '%s')
                    """.formatted(reservationId, UUID.randomUUID(), activityId, Timestamp.from(STARTS_AT),
                    Timestamp.from(STARTS_AT.minusSeconds(7200)), Timestamp.from(STARTS_AT)));
            // The quarantine table is recreated from the current reservations shape when the
            // history is dropped; leaving the old shape would make V2 insert one column less.
            statement.execute("DROP TABLE IF EXISTS reservations_quarantine");
            statement.execute("DROP TABLE flyway_schema_history");
        }

        flyway().migrate();

        try (var connection = connection()) {
            assertThat(count(connection, "reservations")).isEqualTo(1);
            assertThat(count(connection, "reservations_quarantine")).isZero();
        }
    }

    private static Flyway flyway() {
        return baseFlyway().load();
    }

    private static Flyway flywayAtV1() {
        return baseFlyway().target(MigrationVersion.fromVersion("1")).load();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration baseFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insertActivity(Connection connection, UUID activityId) throws SQLException {
        insertActivity(connection, activityId, 10, STARTS_AT);
    }

    private static void insertActivity(Connection connection, UUID activityId, int capacity,
                                       Instant startsAt) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO actividad (id, sitio_id, name, category, status, price, max_capacity,
                                           duration_mins, starts_at, ends_at)
                    VALUES ('%s', '%s', 'Kayak', 'Tour', 'PUBLISHED', 100.00, %d, 60,
                            '%s', '%s')
                    """.formatted(activityId, UUID.randomUUID(), capacity,
                    Timestamp.from(startsAt), Timestamp.from(startsAt.plusSeconds(3600))));
        }
    }

    private static void insertReservation(Connection connection, UUID reservationId, int people,
                                          String total, String status, Instant bookedFor) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO reservations (id, tourist_id, number_of_people, total, payment_method,
                                              status, booked_at, booked_for)
                    VALUES ('%s', '%s', %d, %s, 'CASH', '%s', '%s', '%s')
                    """.formatted(reservationId, UUID.randomUUID(), people, total, status,
                    Timestamp.from(Instant.parse("2026-01-01T08:00:00Z")), Timestamp.from(bookedFor)));
        }
    }

    private static void insertItem(Connection connection, UUID reservationId, UUID itemId,
                                   String type, String price) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO reservation_items (id, reservation_id, item_id, type, price)
                    VALUES ('%s', '%s', '%s', '%s', %s)
                    """.formatted(UUID.randomUUID(), reservationId, itemId, type, price));
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        return countWhere(connection, table, null);
    }

    private static long countWhere(Connection connection, String table, String predicate) throws SQLException {
        var sql = "SELECT count(*) FROM " + table + (predicate == null ? "" : " WHERE " + predicate);
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT to_regclass('public.' || ?)")) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getObject(1) != null;
            }
        }
    }
}
