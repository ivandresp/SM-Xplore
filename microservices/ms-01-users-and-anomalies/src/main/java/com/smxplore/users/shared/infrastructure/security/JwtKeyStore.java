package com.smxplore.users.shared.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.shared.infrastructure.config.JwtProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carga la pareja de llaves RSA usada para firmar los JWT.
 *
 * <p>Orden de resolución:
 *
 * <ol>
 *   <li>{@code app.security.jwt.private-key} + {@code public-key} (PEM o base64 PKCS#8/X.509).
 *   <li>Archivo en {@code app.security.jwt.key-store-path}.
 *   <li>Generación local si {@code allow-generated-keys} está habilitado; la pareja se guarda en disco
 *       para que los tokens sobrevivan a un reinicio.
 * </ol>
 *
 * <p>Si no hay llaves y la generación está deshabilitada se falla al arrancar en lugar de emitir tokens con
 * una llave efímera.
 */
final class JwtKeyStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtKeyStore.class);
    private static final Pattern PRIVATE_PEM =
            Pattern.compile("-----BEGIN PRIVATE KEY-----(.*?)-----END PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern PUBLIC_PEM =
            Pattern.compile("-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----", Pattern.DOTALL);
    private static final int KEY_SIZE = 2048;
    private static final int PEM_LINE_LENGTH = 64;

    private JwtKeyStore() {}

    static RSAKey load(JwtProperties properties) {
        if (hasText(properties.privateKey())) {
            return withThumbprintKeyId(fromPem(properties.privateKey(), properties.publicKey()));
        }

        Path path = properties.keyStorePath();
        if (path != null && Files.isRegularFile(path)) {
            return withThumbprintKeyId(readFromFile(path));
        }

        if (!properties.allowGeneratedKeys()) {
            throw new IllegalStateException("No hay llaves JWT configuradas. Define JWT_PRIVATE_KEY y "
                    + "JWT_PUBLIC_KEY, o habilita app.security.jwt.allow-generated-keys solo en desarrollo.");
        }

        RSAKey generated = withThumbprintKeyId(generate());
        if (path != null) {
            writeToFile(path, generated);
            LOGGER.warn("Se generó una pareja de llaves JWT de desarrollo en {}", path.toAbsolutePath());
        } else {
            LOGGER.warn("Se generó una pareja de llaves JWT en memoria; los tokens se invalidarán al reiniciar");
        }
        return generated;
    }

    private static RSAKey fromPem(String privateKey, String publicKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] privateBytes = decodePem(privateKey, PRIVATE_PEM);
            byte[] publicBytes = decodePem(publicKey, PUBLIC_PEM);
            return new RSAKey.Builder((RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes)))
                    .privateKey((RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)))
                    .build();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudieron interpretar las llaves JWT configuradas", exception);
        }
    }

    private static RSAKey readFromFile(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el almacén de llaves JWT en " + path, exception);
        }
        Matcher privateMatcher = PRIVATE_PEM.matcher(content);
        Matcher publicMatcher = PUBLIC_PEM.matcher(content);
        if (!privateMatcher.find() || !publicMatcher.find()) {
            throw new IllegalStateException("El almacén de llaves JWT en " + path + " no contiene ambas llaves PEM");
        }
        return fromPem(privateMatcher.group(), publicMatcher.group());
    }

    private static void writeToFile(Path path, RSAKey key) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    toPem("PRIVATE KEY", key.toRSAPrivateKey().getEncoded())
                            + toPem("PUBLIC KEY", key.toRSAPublicKey().getEncoded()),
                    StandardCharsets.UTF_8);
        } catch (IOException | JOSEException exception) {
            throw new IllegalStateException("No se pudo guardar el almacén de llaves JWT en " + path, exception);
        }
    }

    private static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .build();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo generar la llave RSA", exception);
        }
    }

    private static RSAKey withThumbprintKeyId(RSAKey key) {
        try {
            return new RSAKey.Builder(key)
                    .keyID(key.computeThumbprint().toString())
                    .build();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo calcular el identificador de la llave JWT", exception);
        }
    }

    private static byte[] decodePem(String value, Pattern blockPattern) {
        Matcher matcher = blockPattern.matcher(value);
        String base64 = matcher.find() ? matcher.group(1) : value;
        return Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
    }

    private static String toPem(String type, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder pem = new StringBuilder("-----BEGIN ").append(type).append("-----\n");
        for (int index = 0; index < base64.length(); index += PEM_LINE_LENGTH) {
            pem.append(base64, index, Math.min(index + PEM_LINE_LENGTH, base64.length()))
                    .append('\n');
        }
        return pem.append("-----END ").append(type).append("-----\n").toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
