package com.smxplore.sitesandservices;

import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ActiveProfiles("test")
@Import(TestJwtConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractMongoPostgresIntegrationTest {

    protected static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:7.0").withReplicaSet();

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("smxplore_test")
                    .withUsername("smxplore")
                    .withPassword("test-password");

    static {
        MONGO.start();
        reconfigureMongoReplicaSet();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> mongoUri());
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                        + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected SiteRepositoryPort siteRepository;

    /**
     * Los servicios guardan el sitio como snapshot, pero el flujo de reserva consulta
     * el estado actual del sitio. Por eso los fixtures deben persistirlo.
     */
    protected Site persistPublishedSite(String name) {
        Instant now = Instant.now();
        Site site = Site.builder()
                .siteId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .name(name)
                .description("Sitio de prueba")
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría de prueba")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona de prueba")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(10.39)
                .longitude(-75.47)
                .photosUrls(List.of())
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(100)
                .availability(List.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return siteRepository.save(site).block();
    }

    private static void reconfigureMongoReplicaSet() {
        String ip = mongoIp();
        try {
            MONGO.execInContainer(
                    "mongosh",
                    "--quiet",
                    "--eval",
                    "cfg=rs.conf(); cfg.members[0].host='" + ip + ":27017'; rs.reconfig(cfg,{force:true})"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not reconfigure MongoDB replica set", exception);
        }
    }

    private static String mongoUri() {
        // Docker Desktop (Windows/macOS) no enruta la IP interna del contenedor, así que
        // el cliente entra por el puerto publicado y desactiva el descubrimiento del
        // replica set con directConnection; el nodo sigue siendo el primario de docker-rs.
        return "mongodb://localhost:" + MONGO.getMappedPort(27017)
                + "/smxplore_test?replicaSet=docker-rs&directConnection=true";
    }

    private static String mongoIp() {
        return MONGO.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
    }
}
