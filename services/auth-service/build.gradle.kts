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
    // BOM imported explicitly — one place controls every Spring version
    implementation(platform(libs.spring.boot.dependencies))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JWT issue/verify (nimbus-jose-jwt via spring-security-oauth2-jose) + bearer-token auth
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // Argon2PasswordEncoder delegates to BouncyCastle — optional in
    // spring-security-crypto, so it must be declared explicitly (not in the Boot BOM)
    implementation(libs.bouncycastle)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // springdoc 3.x is the Spring Boot 4 line (2.x targets Boot 3)
    implementation(libs.springdoc.openapi)
    // verification emails: Mailpit locally, Gmail SMTP via env in deployed envs (ADR 004)
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // official Resend SDK (replaces the hand-rolled RestClient integration)
    implementation(libs.resend)

    // Boot 4: Flyway auto-configuration lives in its own starter — flyway-core
    // alone on the classpath no longer triggers migrations at startup
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 modularized test support: MockMvc auto-config lives in the
    // per-technology webmvc-test module, no longer inside starter-test
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x (managed by the Boot BOM) renamed its modules:
    // postgresql → testcontainers-postgresql, junit-jupiter → testcontainers-junit-jupiter
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    // Karate drives black-box API tests over real HTTP (Gherkin-style features).
    // GroupId is io.karatelabs since 1.5.x (com.intuit.karate stopped at 1.4.1).
    testImplementation(libs.karate.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // coverage data is what Sonar and the verification gate read
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    // 0.8.13+ understands Java 25 class files
    toolVersion = "0.8.13"
}

// classes with no meaningful branch logic to cover
val coverageExclusions = listOf(
    "com/fintrack/auth/AuthServiceApplication.class"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true   // consumed by Sonar
        html.required = true  // human-readable, build/reports/jacoco
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

// `./gradlew build` fails if coverage drops below 90% — same gate locally and in CI
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

sonar {
    properties {
        property("sonar.projectKey", "fintrack-auth-service")
        property("sonar.projectName", "FinTrack auth-service")
        // local SonarQube container (compose profile "quality"); token via SONAR_TOKEN env var
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path)
        property("sonar.coverage.exclusions", "**/AuthServiceApplication.java")
    }
}
