plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
}

group = "com.smxplore"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    implementation(libs.spring.boot.webflux)
    implementation(libs.spring.boot.data.r2dbc)
    implementation(libs.spring.boot.data.mongodb)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.flyway)
    implementation(libs.spring.boot.mail)
    implementation("org.springframework.kafka:spring-kafka")
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.oauth2.resource.server)
    implementation(libs.springdoc.webflux.ui)
    implementation(libs.caffeine)
    implementation(libs.openpdf)

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)
    runtimeOnly(libs.flyway.postgresql)

    // Fuerza scram 3.3: la 3.2 tiene CVE-2026-53712 (downgrade de autenticación SCRAM).
    constraints {
        implementation(libs.scram.client)
        implementation(libs.scram.common)
    }

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.reactor.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.r2dbc)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// kafka-clients arrastra lz4-java y snappy-java con CVEs HIGH y sus versiones parcheadas no
// estan publicadas; ms-01 no usa compresion Kafka, asi que se excluyen del classpath.
configurations.configureEach {
    exclude(group = "org.lz4", module = "lz4-java")
    exclude(group = "org.xerial.snappy", module = "snappy-java")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.java.format.get())
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
