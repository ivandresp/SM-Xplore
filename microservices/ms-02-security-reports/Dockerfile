# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon --console=plain

COPY src ./src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0

RUN set -eux; \
    ok=0; \
    for intento in 1 2 3; do \
        if apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=30 update \
            && apt-get install -y --no-install-recommends -o Acquire::Retries=5 -o Acquire::http::Timeout=30 curl apt; then \
            ok=1; \
            break; \
        fi; \
        echo "reintento de apt ${intento}/3"; \
        sleep 5; \
    done; \
    test "${ok}" = "1"; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd --system app; \
    useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
