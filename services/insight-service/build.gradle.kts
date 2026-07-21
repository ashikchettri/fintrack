plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
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
    implementation(platform(libs.spring.boot.dependencies))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // verifies auth-service JWTs against its JWKS endpoint — no shared secrets
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // interactive API docs at /swagger-ui.html
    implementation(libs.springdoc.openapi)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
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
    "com/fintrack/insight/InsightServiceApplication.class",
    // Spring wiring only (bean config + properties) — behaviour covered by unit tests
    "com/fintrack/insight/config/AiSummaryConfig.class",
    "com/fintrack/insight/config/AiSummaryProperties.class"
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
        property("sonar.projectKey", "fintrack-insight-service")
        property("sonar.projectName", "FinTrack insight-service")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path)
        property("sonar.coverage.exclusions", "**/InsightServiceApplication.java")
    }
}
