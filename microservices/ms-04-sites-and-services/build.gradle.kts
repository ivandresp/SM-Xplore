plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.smxplore"
version = "0.0.1-SNAPSHOT"
description = "ms-04-sites-and-services"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.awaitility:awaitility")
    testImplementation("com.networknt:json-schema-validator:1.5.9")

    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Dependencias transitivas con CVE corregible que detecta el escaneo de Trivy del pipeline.
    // Los drivers de PostgreSQL arrastran SCRAM y el cliente de Kafka arrastra lz4 y snappy.
    constraints {
        implementation("at.yawk.lz4:lz4-java:1.11.4") {
            because("CVE-2026-106451, ejecución de código por manejo inseguro de temporales")
        }
        implementation("com.ongres.scram:scram-client:3.3") {
            because("CVE-2026-53712, degradación de autenticación por man-in-the-middle")
        }
        implementation("com.ongres.scram:scram-common:3.3") {
            because("CVE-2026-53712, degradación de autenticación por man-in-the-middle")
        }
        implementation("org.xerial.snappy:snappy-java:1.1.10.9") {
            because("CVE-2026-108106 y CVE-2026-93451, desbordamiento de búfer y denegación de servicio")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("JWT_ISSUER", "https://ms-04.local")
    systemProperty("contracts.dir", file("../../contracts/events").absolutePath)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
