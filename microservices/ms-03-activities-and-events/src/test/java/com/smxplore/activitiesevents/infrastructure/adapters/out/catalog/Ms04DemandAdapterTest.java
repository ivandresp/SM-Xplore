package com.smxplore.activitiesevents.infrastructure.adapters.out.catalog;

import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandGranularity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Ms04DemandAdapterTest {

    @Test
    void parsesTheServiceDemandResponseFromMs04() {
        try (StubMs04 server = StubMs04.start()) {
            var adapter = new Ms04DemandAdapter(server.baseUrl(), Duration.ofSeconds(2));
            var filter = new DemandFilter(Instant.parse("2026-10-01T00:00:00Z"),
                    Instant.parse("2026-11-01T00:00:00Z"), null, null, DemandGranularity.DAY);

            StepVerifier.create(adapter.fetch(filter, "token"))
                    .assertNext(demand -> {
                        assertThat(demand.summary().reservations()).isEqualTo(2);
                        assertThat(demand.summary().people()).isEqualTo(5);
                        assertThat(demand.services()).hasSize(1);
                        assertThat(demand.services().getFirst().serviceName()).isEqualTo("Tour");
                        assertThat(demand.zones()).hasSize(1);
                        assertThat(demand.zones().getFirst().zoneName()).isEqualTo("Zona Norte");
                        assertThat(demand.timeline()).hasSize(1);
                        assertThat(server.authorization()).isEqualTo("Bearer token");
                    })
                    .verifyComplete();
        }
    }

    private static final class StubMs04 implements AutoCloseable {

        private final HttpServer server;
        private volatile String authorization;

        private StubMs04(HttpServer server) {
            this.server = server;
            this.server.createContext("/api/v1/demand-indicators", this::handle);
            this.server.start();
        }

        static StubMs04 start() {
            try {
                return new StubMs04(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String authorization() {
            return authorization;
        }

        private void handle(HttpExchange exchange) throws IOException {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            var zoneId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            var siteId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var body = """
                    {
                      "summary": {"reservations": 2, "people": 5, "revenue": 50000.00,
                        "cancelledReservations": 0, "completedReservations": 1, "uniqueTourists": 2,
                        "cancellationRate": 0.0, "averagePeoplePerReservation": 2.5},
                      "comparison": {
                        "current": {"reservations": 2, "people": 5, "revenue": 50000.00,
                          "cancelledReservations": 0, "completedReservations": 1, "uniqueTourists": 2,
                          "cancellationRate": 0.0, "averagePeoplePerReservation": 2.5},
                        "previous": {"reservations": 1, "people": 2, "revenue": 20000.00,
                          "cancelledReservations": 0, "completedReservations": 1, "uniqueTourists": 1,
                          "cancellationRate": 0.0, "averagePeoplePerReservation": 2.0},
                        "reservationsChange": 100.0, "peopleChange": 150.0,
                        "revenueChange": 150.0, "cancellationRateChange": 0.0
                      },
                      "timeline": [{"period": "2026-10-01", "reservations": 2, "people": 5,
                        "revenue": 50000.00}],
                      "weekdays": [{"key": "MONDAY", "label": "lunes", "reservations": 2, "people": 5}],
                      "hours": [{"key": "10", "label": "10:00", "reservations": 2, "people": 5}],
                      "zones": [{"zoneId": "%s", "zoneName": "Zona Norte", "zoneStatus": "AVAILABLE",
                        "reservations": 2, "people": 5, "revenue": 50000.00, "share": 1.0,
                        "capacityPeople": 20, "occupancyRate": 0.25}],
                      "sites": [{"siteId": "%s", "siteName": "Playa", "zoneId": "%s",
                        "zoneName": "Zona Norte", "reservations": 2, "people": 5, "revenue": 50000.00,
                        "share": 1.0, "capacityPeople": 20, "occupancyRate": 0.25}],
                      "services": [{"serviceId": "33333333-3333-3333-3333-333333333333",
                        "serviceName": "Tour", "serviceType": "TOUR", "siteId": "%s",
                        "siteName": "Playa", "zoneId": "%s", "zoneName": "Zona Norte",
                        "reservations": 2, "people": 5, "revenue": 50000.00, "share": 1.0,
                        "capacityPeople": 20, "occupancyRate": 0.25}]
                    }
                    """.formatted(zoneId, siteId, zoneId, siteId, zoneId);
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
