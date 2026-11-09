import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Healthcheck sin dependencias externas para la imagen del servicio.
 *
 * <p>Consulta Actuator y termina con codigo 0 cuando responde 200 y el estado es UP. Sustituye
 * al healthcheck anterior basado en curl: la imagen ya no instala paquetes del sistema durante el
 * build y no expone ni apt ni curl en runtime.</p>
 */
public final class HealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private HealthCheck() {
    }

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://127.0.0.1:8080/actuator/health";
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
            System.exit(healthy ? 0 : 1);
        } catch (Exception exception) {
            System.err.println("healthcheck failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            System.exit(1);
        }
    }
}