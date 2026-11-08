package com.smxplore.users.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Autentica llamadas servicio-a-servicio al endpoint interno.
 *
 * <p>MS01-11: antes cualquier portador de {@code X-Internal-Api-Key} podía leer los datos de contacto de cualquier
 * usuario sin dejar rastro ni decir quién era. Ahora cada servicio firma la petición con su propia clave derivada y la
 * cabecera {@code X-Internal-Service} identifica al emisor, que queda registrado en {@code internal_access_log}.
 *
 * <p>Cadena firmada: {@code servicio + "\n" + método + "\n" + ruta + "\n" + timestamp}. La clave por servicio se deriva
 * como {@code HMAC-SHA256(clave maestra, servicio)}, de modo que rotar la clave maestra invalida todas las derivadas y
 * una filtración de una clave de servicio no compromete las demás.
 */
@Component
public class InternalRequestAuthenticator {

    public enum Outcome {
        OK,
        INVALID_SERVICE,
        INVALID_SIGNATURE
    }

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final HexFormat HEX = HexFormat.of();

    private final InternalApiProperties properties;
    private final Clock clock;
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(10_000)
            .build();

    public InternalRequestAuthenticator(InternalApiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Outcome authenticate(String service, String timestamp, String signature, String method, String path) {
        if (!validServiceName(service)
                || (!properties.allowedServices().isEmpty()
                        && !properties.allowedServices().contains(service))) {
            return Outcome.INVALID_SERVICE;
        }
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            return Outcome.INVALID_SIGNATURE;
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            return Outcome.INVALID_SIGNATURE;
        }
        Duration skew = Duration.between(Instant.ofEpochSecond(epochSeconds), clock.instant())
                .abs();
        if (skew.compareTo(properties.maxClockSkew()) > 0) {
            return Outcome.INVALID_SIGNATURE;
        }
        String signingString = service + "\n" + method + "\n" + path + "\n" + timestamp.trim();
        String expected = HEX.formatHex(hmac(serviceKey(service), signingString.getBytes(StandardCharsets.UTF_8)));
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return matches ? Outcome.OK : Outcome.INVALID_SIGNATURE;
    }

    /** Límite por servicio (además del límite general por IP), ventana fija de un minuto. */
    public boolean tryAcquire(String service) {
        if (service == null || service.isBlank()) {
            return false;
        }
        Window window = windows.get(service, key -> new Window(clock.instant()));
        return window == null || window.tryAcquire(clock.instant(), properties.rateLimitPerMinute());
    }

    private static boolean validServiceName(String service) {
        return service != null && service.matches("[a-z0-9-]{2,32}");
    }

    private byte[] serviceKey(String service) {
        return hmac(properties.apiKey().getBytes(StandardCharsets.UTF_8), service.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 no está disponible en la JVM", exception);
        }
    }

    private static final class Window {

        private Instant start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant start) {
            this.start = start;
        }

        private synchronized boolean tryAcquire(Instant now, int limit) {
            if (!now.isBefore(start.plus(Duration.ofMinutes(1)))) {
                start = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
