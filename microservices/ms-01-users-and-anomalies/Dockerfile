# syntax=docker/dockerfile:1
## Imagen de ms-01: build multi-etapa con el Gradle Wrapper y runtime JRE sin privilegios.

FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f AS build

WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle-home

# Primero sólo el build: así la descarga de Gradle y de los plugins queda en una capa cacheable.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --console=plain help

COPY src ./src
COPY healthcheck ./healthcheck
RUN javac -d /workspace/healthcheck/classes /workspace/healthcheck/HealthCheck.java
RUN ./gradlew --no-daemon --console=plain bootJar \
    && JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c AS runtime

LABEL org.opencontainers.image.title="ms-01-users-and-anomalies" \
      org.opencontainers.image.description="Identidad, reseñas y anomalías de SM-Xplore" \
      org.opencontainers.image.source="https://github.com/ivandresp/SM-Xplore"

RUN set -eux; \
    dpkg --purge apt 2>/dev/null || true; \
    dpkg --purge libapt-pkg6.0t64 2>/dev/null || true; \
    dpkg --purge libapt-pkg7.0 2>/dev/null || true; \
    rm -rf /var/lib/apt/lists/* /etc/apt /var/log/apt; \
    groupadd --system app; \
    useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /workspace/healthcheck/classes /app/healthcheck
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
# El perfil dev genera las llaves JWT en .local/jwt; el directorio debe ser escribible por el usuario sin privilegios.
RUN mkdir -p /app/.local/jwt && chown -R app:app /app

USER app
EXPOSE 8081
# Perfil explícito: la imagen sin SPRING_PROFILES_ACTIVE no debe caer en valores de desarrollo.
ENV SPRING_PROFILES_ACTIVE=prod

# 75 % de la memoria del contenedor, acceso nativo para Netty y salida limpia ante OutOfMemoryError.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD ["java", "-cp", "/app/healthcheck", "HealthCheck", "http://127.0.0.1:8081/actuator/health"]
