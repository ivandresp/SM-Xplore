plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    implementation(libs.spring.boot.webmvc)
    implementation(libs.spring.boot.data.jpa)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.flyway)
    implementation(libs.spring.boot.mail)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.oauth2.resource.server)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.caffeine)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.testcontainers.postgresql)
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
