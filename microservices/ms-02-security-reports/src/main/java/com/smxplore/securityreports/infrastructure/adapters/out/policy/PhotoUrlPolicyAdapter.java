package com.smxplore.securityreports.infrastructure.adapters.out.policy;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.domain.ports.out.PhotoUrlPolicyPort;
import com.smxplore.securityreports.infrastructure.config.PhotoUrlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Valida las URLs de fotos contra la allowlist configurada (MS02-07).
 *
 * <p>El servicio no sube archivos: guarda URLs que luego carga el navegador. Sin allowlist un atacante puede
 * apuntar a su propio host (seguimiento de la IP del staff, contenido mixto) o usar {@code http}. Se exige
 * {@code https} y un host permitido; los subdominios se declaran con {@code *.dominio}.
 */
@Component
@RequiredArgsConstructor
public class PhotoUrlPolicyAdapter implements PhotoUrlPolicyPort {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    private final PhotoUrlProperties properties;

    @Override
    public void validate(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidReportRequestException("Photo URL is not a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!HTTPS.equals(scheme) && !(properties.isAllowHttp() && HTTP.equals(scheme))) {
            throw new InvalidReportRequestException("Photo URL must use https.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidReportRequestException("Photo URL must include a host.");
        }
        if (properties.isAllowAnyHost()) {
            return;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean allowed = properties.getAllowedHosts().stream()
                .anyMatch(entry -> matches(normalizedHost, entry));
        if (!allowed) {
            throw new InvalidReportRequestException("Photo URL host is not allowed: " + host + ".");
        }
    }

    private static boolean matches(String host, String entry) {
        if (entry.startsWith("*.")) {
            String suffix = entry.substring(1);
            return host.length() > suffix.length() && host.endsWith(suffix);
        }
        return host.equals(entry);
    }
}
