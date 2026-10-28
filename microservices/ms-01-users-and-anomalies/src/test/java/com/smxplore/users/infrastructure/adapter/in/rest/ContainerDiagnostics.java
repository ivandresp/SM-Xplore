package com.smxplore.users.infrastructure.adapter.in.rest;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.bson.Document;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.core.publisher.Mono;

/**
 * Utilidades de diagnóstico para las pruebas con MongoDB. La comprobación de disponibilidad convierte un fallo de
 * infraestructura en un error claro y los logs del contenedor quedan en {@code build/container-logs} para revisarlos
 * cuando la ejecución falla.
 */
final class ContainerDiagnostics {

    private ContainerDiagnostics() {}

    static void verifyMongoReachable(MongoDBContainer mongo) {
        String uri = mongo.getConnectionString();
        try (MongoClient client = MongoClients.create(uri)) {
            Mono.from(client.getDatabase("admin").runCommand(new Document("ping", 1)))
                    .block(Duration.ofSeconds(15));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("MongoDB no responde en " + uri, exception);
        }
    }

    static void dumpMongoLogs(MongoDBContainer mongo, String label) {
        try {
            Path directory = Path.of("build", "container-logs");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(label + "-mongo.log"), mongo.getLogs());
        } catch (Exception ignored) {
            // El diagnóstico nunca debe hacer fallar la prueba.
        }
    }
}
