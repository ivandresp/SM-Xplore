package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReservationPostgresAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static R2dbcEntityTemplate template;
    private static ReservationPostgresAdapter adapter;

    @BeforeAll
    static void setUp() {
        var url = "r2dbc:postgresql://%s:%s@%s:%d/%s".formatted(
                POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        ConnectionFactory connectionFactory = ConnectionFactories.get(url);
        template = new R2dbcEntityTemplate(connectionFactory);
        adapter = new ReservationPostgresAdapter(null, template, null);

        execute("CREATE TABLE evento (id UUID PRIMARY KEY, status VARCHAR(30) NOT NULL, ends_at TIMESTAMPTZ NOT NULL)");
        execute("CREATE TABLE actividad (id UUID PRIMARY KEY, status VARCHAR(30) NOT NULL, ends_at TIMESTAMPTZ NOT NULL)");
        execute("""
                CREATE TABLE reservations (
                    id UUID PRIMARY KEY,
                    item_type VARCHAR(20) NOT NULL,
                    item_id UUID NOT NULL,
                    status VARCHAR(30) NOT NULL
                )
                """);
    }

    @Test
    void closesOnlyConfirmedReservationsOfPublishedItemsThatEndedBeforeTheCutoff() {
        var now = Instant.now();
        var ended = now.minus(Duration.ofDays(2));
        var future = now.plus(Duration.ofDays(2));

        var publishedEvent = insertItem("evento", "PUBLISHED", ended);
        var expiredEvent = insertReservation("EVENT", publishedEvent, "CONFIRMED");

        var publishedActivity = insertItem("actividad", "PUBLISHED", ended);
        var expiredActivity = insertReservation("ACTIVITY", publishedActivity, "CONFIRMED");

        var cancelledEvent = insertItem("evento", "CANCELLED", ended);
        var cancelledEventReservation = insertReservation("EVENT", cancelledEvent, "CONFIRMED");

        var cancelledActivity = insertItem("actividad", "CANCELLED", ended);
        var cancelledActivityReservation = insertReservation("ACTIVITY", cancelledActivity, "CONFIRMED");

        var pausedActivity = insertItem("actividad", "PAUSED", ended);
        var pausedActivityReservation = insertReservation("ACTIVITY", pausedActivity, "CONFIRMED");

        var futureEvent = insertItem("evento", "PUBLISHED", future);
        var futureReservation = insertReservation("EVENT", futureEvent, "CONFIRMED");

        var recentEvent = insertItem("evento", "PUBLISHED", now.minus(Duration.ofHours(1)));
        var recentReservation = insertReservation("EVENT", recentEvent, "CONFIRMED");

        var futureCancelledEvent = insertItem("evento", "CANCELLED", future);
        var futureCancelledReservation = insertReservation("EVENT", futureCancelledEvent, "CONFIRMED");

        var completedReservation = insertReservation("EVENT", publishedEvent, "COMPLETED");

        var closed = adapter.closeStaleConfirmed(now.minus(Duration.ofDays(1))).block();

        assertThat(closed).isEqualTo(5);
        assertThat(statusOf(expiredEvent)).isEqualTo("NO_SHOW");
        assertThat(statusOf(expiredActivity)).isEqualTo("NO_SHOW");
        assertThat(statusOf(cancelledEventReservation)).isEqualTo("CANCELLED");
        assertThat(statusOf(cancelledActivityReservation)).isEqualTo("CANCELLED");
        assertThat(statusOf(pausedActivityReservation)).isEqualTo("CONFIRMED");
        assertThat(statusOf(futureReservation)).isEqualTo("CONFIRMED");
        assertThat(statusOf(recentReservation)).isEqualTo("CONFIRMED");
        assertThat(statusOf(futureCancelledReservation)).isEqualTo("CANCELLED");
        assertThat(statusOf(completedReservation)).isEqualTo("COMPLETED");
    }

    private static UUID insertItem(String table, String status, Instant endsAt) {
        var id = UUID.randomUUID();
        template.getDatabaseClient()
                .sql("INSERT INTO " + table + " (id, status, ends_at) VALUES (:id, :status, :endsAt)")
                .bind("id", id)
                .bind("status", status)
                .bind("endsAt", endsAt)
                .then()
                .block();
        return id;
    }

    private static UUID insertReservation(String itemType, UUID itemId, String status) {
        var id = UUID.randomUUID();
        template.getDatabaseClient()
                .sql("INSERT INTO reservations (id, item_type, item_id, status) "
                        + "VALUES (:id, :itemType, :itemId, :status)")
                .bind("id", id)
                .bind("itemType", itemType)
                .bind("itemId", itemId)
                .bind("status", status)
                .then()
                .block();
        return id;
    }

    private static String statusOf(UUID reservationId) {
        return template.getDatabaseClient()
                .sql("SELECT status FROM reservations WHERE id = :id")
                .bind("id", reservationId)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .block();
    }

    private static void execute(String sql) {
        template.getDatabaseClient().sql(sql).then().block();
    }
}
