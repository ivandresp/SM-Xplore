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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

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
        }
        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void legacyReservationsAreBackfilledAndNormalized() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        var orphanId = UUID.randomUUID();
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO actividad (id, sitio_id, name, category, status, price, max_capacity,
                                           duration_mins, starts_at, ends_at)
                    VALUES ('%s', '%s', 'Kayak', 'Tour', 'PUBLISHED', 100.00, 10, 60,
                            now(), now() + interval '1 hour')
                    """.formatted(activityId, UUID.randomUUID()));
            statement.execute("""
                    INSERT INTO reservations (id, tourist_id, number_of_people, total, payment_method,
                                              status, booked_at, booked_for)
                    VALUES ('%s', '%s', 2, 200.00, 'CASH', 'PENDING', now(), now() + interval '1 hour')
                    """.formatted(reservationId, UUID.randomUUID()));
            statement.execute("""
                    INSERT INTO reservation_items (id, reservation_id, item_id, type, price)
                    VALUES ('%s', '%s', '%s', 'ACTIVITY', 100.00)
                    """.formatted(UUID.randomUUID(), reservationId, activityId));
            statement.execute("""
                    INSERT INTO reservations (id, tourist_id, number_of_people, total, payment_method,
                                              status, booked_at, booked_for)
                    VALUES ('%s', '%s', 1, 50.00, 'CASH', 'PENDING', now(), now() + interval '2 hours')
                    """.formatted(orphanId, UUID.randomUUID()));
        }

        flyway().migrate();

        try (var connection = connection()) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT status, item_type, unit_price, item_starts_at FROM reservations")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("CONFIRMED");
                assertThat(result.getString("item_type")).isEqualTo("ACTIVITY");
                assertThat(result.getBigDecimal("unit_price")).isEqualByComparingTo("100.00");
                assertThat(result.getTimestamp("item_starts_at")).isNotNull();
                assertThat(result.next()).isFalse();
            }
            assertThat(tableExists(connection, "reservation_items")).isFalse();
        }
    }

    @Test
    void existingLegacySchemaIsBaselinedAndMigrated() throws Exception {
        flywayAtV1().migrate();

        var activityId = UUID.randomUUID();
        var reservationId = UUID.randomUUID();
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO actividad (id, sitio_id, name, category, status, price, max_capacity,
                                           duration_mins, starts_at, ends_at)
                    VALUES ('%s', '%s', 'Kayak', 'Tour', 'PUBLISHED', 100.00, 10, 60,
                            now(), now() + interval '1 hour')
                    """.formatted(activityId, UUID.randomUUID()));
            statement.execute("""
                    INSERT INTO reservations (id, tourist_id, number_of_people, total, payment_method,
                                              status, booked_at, booked_for)
                    VALUES ('%s', '%s', 2, 200.00, 'CASH', 'PENDING', now(), now() + interval '1 hour')
                    """.formatted(reservationId, UUID.randomUUID()));
            statement.execute("""
                    INSERT INTO reservation_items (id, reservation_id, item_id, type, price)
                    VALUES ('%s', '%s', '%s', 'ACTIVITY', 100.00)
                    """.formatted(UUID.randomUUID(), reservationId, activityId));
            statement.execute("DROP TABLE flyway_schema_history");
        }

        flyway().migrate();

        try (var connection = connection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT version FROM flyway_schema_history ORDER BY installed_rank")) {
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
                .baselineVersion("1");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
