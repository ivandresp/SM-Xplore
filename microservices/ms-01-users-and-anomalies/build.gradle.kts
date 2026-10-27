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
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.oauth2.resource.server)
    implementation(libs.springdoc.webflux.ui)
    implementation(libs.caffeine)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)
    runtimeOnly(libs.flyway.postgresql)

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
