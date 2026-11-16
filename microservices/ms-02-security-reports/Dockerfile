# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk@sha256:b8705e936964c228dfce359e63dbf08f6c69cb380ed1d576b1f6b1064f15567d AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon --console=plain

COPY src ./src
COPY healthcheck ./healthcheck
RUN javac -d /workspace/healthcheck/classes /workspace/healthcheck/HealthCheck.java
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre@sha256:a640968ae55027b322cedd5f8228b01a84baff0b9e9360e47601c6a111ba1699

RUN set -eux; \
    dpkg --purge apt 2>/dev/null || true; \
    dpkg --purge libapt-pkg6.0t64 2>/dev/null || true; \
    dpkg --purge libapt-pkg7.0 2>/dev/null || true; \
    rm -rf /var/lib/apt/lists/* /etc/apt /var/log/apt; \
    groupadd --system app; \
    useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build /workspace/healthcheck/classes /app/healthcheck
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=5 \
    CMD ["java", "-cp", "/app/healthcheck", "HealthCheck", "http://127.0.0.1:8080/actuator/health"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
