package com.smxplore.sitesandservices;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
        registry.add("spring.sql.init.mode", () -> "always");
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
        return "mongodb://" + mongoIp() + ":27017/smxplore_test?replicaSet=docker-rs";
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
