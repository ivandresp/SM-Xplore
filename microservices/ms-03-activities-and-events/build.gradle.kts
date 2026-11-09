plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.smxplore"
version = "0.0.1-SNAPSHOT"
description = "activities-and-events"

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
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // starter y no spring-kafka suelto: sin el auto-configurador de Kafka no hay KafkaTemplate ni
    // contenedor de listeners, asi que el relay del outbox y el consumidor de sitios no arrancaban.
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.1.0")
    implementation("org.mapstruct:mapstruct:1.6.3")
    // Reporte PDF de planificacion; ya se usa en ms-01 con la misma version.
    implementation("com.github.librepdf:openpdf:3.0.5")

    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // El cliente de Kafka arrastra compresion nativa; se fuerzan las versiones con los CVE cerrados
    // que ya usan ms-02 y ms-05, para que el escaneo Trivy del pipeline no marque la imagen de ms-03.
    constraints {
        implementation("at.yawk.lz4:lz4-java:1.11.4") {
            because("CVE-2026-106451: ejecucion arbitraria de codigo en lz4-java 1.11.2")
        }
        implementation("org.xerial.snappy:snappy-java:1.1.10.9") {
            because("CVE-2026-108106 y CVE-2026-93451: denegacion de servicio y desbordamiento en snappy-java 1.1.10.7")
        }
        // El cliente de PostgreSQL autentica con SCRAM y arrastra una version con CVE.
        implementation("com.ongres.scram:scram-client:3.3") {
            because("CVE-2026-53712: fallo de autenticacion en scram-client 3.2")
        }
        implementation("com.ongres.scram:scram-common:3.3") {
            because("Debe seguir la version de scram-client, CVE-2026-53712")
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation("org.testcontainers:testcontainers-kafka")
    // Valida los eventos generados contra contracts/events/*.v1.json, como ms-02 y ms-05.
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    // ArchUnit: reglas de arquitectura espejo del ArchitectureTest de ms-01. ms-03 no usa catalogo de
    // versiones (todas sus dependencias van con version literal), asi que se fija la misma version
    // que declara el catalogo de ms-01 (`archunit = "1.5.1"`).
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("JWT_ISSUER", "sm-xplore-ms-01")
    environment("JWT_AUDIENCE", "sm-xplore-services")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}
