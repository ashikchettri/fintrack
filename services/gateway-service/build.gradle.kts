plugins {
    java
    jacoco
    // The gateway pins Spring Boot one minor BEHIND the other services (ADR 007):
    // Spring Cloud's release train (2025.1.x) supports Boot 4.0.x, not yet 4.1.0.
    // Spring Cloud always trails Boot; the gateway shares no code with the domain
    // services, so tracking the latest Cloud-supported Boot here costs nothing.
    id("org.springframework.boot") version "4.0.0"
    alias(libs.plugins.sonarqube)
}

group = "com.fintrack"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot + Spring Cloud BOMs. Boot is pinned to 4.0.x here (see the
    // plugins block) so it matches the Spring Cloud train — hence the literal
    // BOM version rather than the shared catalog's 4.1.0.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))
    implementation(platform(libs.spring.cloud.dependencies))

    // Reactive Spring Cloud Gateway (ADR 007). NOTE: the Boot 4.1-aligned train
    // renamed the reactive starter to `spring-cloud-starter-gateway-server-webflux`;
    // if resolution fails, fall back to the classic id `spring-cloud-starter-gateway`.
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    // Redis backs the request rate limiter (token bucket, keyed by client IP).
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    // real Redis in the context-loads test — Testcontainers from day one
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.13"
}

val coverageExclusions = listOf(
    "com/fintrack/gateway/GatewayServiceApplication.class"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(classDirectories.files.map {
        fileTree(it) { exclude(coverageExclusions) }
    })
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(classDirectories.files.map {
        fileTree(it) { exclude(coverageExclusions) }
    })
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

sonar {
    properties {
        property("sonar.projectKey", "fintrack-gateway-service")
        property("sonar.projectName", "FinTrack gateway-service")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path)
        property("sonar.coverage.exclusions", "**/GatewayServiceApplication.java")
    }
}
