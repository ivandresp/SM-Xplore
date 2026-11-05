# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon --console=plain

COPY src ./src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app
EXPOSE 8082

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
