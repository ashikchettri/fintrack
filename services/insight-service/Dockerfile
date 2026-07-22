# syntax=docker/dockerfile:1

# ── Build ─────────────────────────────────────────────────────────────────────
# Compile the Spring Boot jar and split it into cache-friendly layers. The build
# CONTEXT is the repo root (not this service dir): settings.gradle.kts reads the
# shared version catalog at ../../gradle/libs.versions.toml, so the image mirrors
# the repo layout. Build with:
#   docker build -f services/insight-service/Dockerfile -t fintrack/insight-service .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Shared version catalog first, then the service. Keeping the repo layout means
# settings.gradle.kts's ../../gradle/… reference resolves unchanged.
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY services/insight-service services/insight-service

WORKDIR /workspace/services/insight-service
# The BuildKit cache mount keeps the Gradle cache warm across builds. Tests are
# NOT run here — they need a Docker daemon (Testcontainers) and already run in the
# CI `backend` job; the image is only built once that job is green.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test \
 && cp build/libs/insight-service-*.jar /workspace/application.jar

# Explode the jar into layers (Boot 4 `tools` jarmode). Third-party dependencies
# change rarely; application code changes every commit — separate layers keep the
# heavy dependency layer cached across deploys.
WORKDIR /workspace
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── Runtime ───────────────────────────────────────────────────────────────────
# Alpine JRE: a small runtime with a tiny CVE surface (a plain Ubuntu JRE ships
# extra tooling we never use). Our services are pure-JVM, so musl is a non-issue.
FROM eclipse-temurin:25-jre-alpine AS runtime

# Patch base OS packages to pick up security fixes newer than the base image tag.
RUN apk --no-cache upgrade

# Never run as root — a fixed unprivileged uid/gid (plays well with K8s
# runAsNonRoot / securityContext).
RUN addgroup -S -g 1001 app \
 && adduser -S -u 1001 -G app -h /app -s /sbin/nologin app
WORKDIR /app

# Copy layers most-stable → most-volatile so Docker reuses the dependency layers
# when only application code changed. `application.jar`'s manifest points its
# Class-Path at ./lib (the dependencies layer), so `java -jar` needs no launcher.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8083

# The JVM already reads cgroup limits; cap heap at a fraction of the container's
# memory so native/off-heap space isn't starved.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "application.jar"]
