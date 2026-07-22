# ADR 015 — Service containerization (multi-stage, layered, non-root)

**Status:** Accepted · 2026-07-22

## Context

The roadmap's next milestone is Kubernetes on GKE, which needs a container image per service. Today the services only run via `./dev.sh` (Gradle `bootRun` on the host); `docker-compose.yml` containerizes just the backing infra (Postgres, Redis, Mailpit, SonarQube). We need an image build that is small, reproducible, cache-friendly, secure by default, and — since every service is an independent Gradle project sharing the root version catalog — uniform enough to copy across all four services.

This ADR sets the pattern, established first on **auth-service** (PR for the first Dockerfile). finance-, gateway- and insight-service follow the same template.

## Decision

1. **Multi-stage build, layered Spring Boot jar.** A `build` stage (`eclipse-temurin:25-jdk`) runs `./gradlew bootJar` and explodes the jar with Boot 4's `tools` jarmode (`java -Djarmode=tools -jar app.jar extract --layers`). The `runtime` stage copies the four layers most-stable → most-volatile (`dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`) so a code-only change reuses the heavy dependency layers. The exploded `application.jar` keeps its `Class-Path: lib/…` manifest, so the entrypoint is a plain `java -jar application.jar` — no launcher needed.

2. **Build context is the repo root, Dockerfile lives in the service dir.** Each `settings.gradle.kts` reads the shared catalog at `../../gradle/libs.versions.toml`, so the image mirrors the repo layout (`docker build -f services/<svc>/Dockerfile .`). A root `.dockerignore` keeps the context lean (no `.git`, `node_modules`, `**/build`, `.env`, docs). Tests are **not** run in the image build — they need a Docker daemon (Testcontainers) and already run in the CI `backend` job; the image is only built after that job is green.

3. **Small, patched, non-root runtime.** `eclipse-temurin:25-jre-alpine` (~314 MB vs ~446 MB for the Ubuntu JRE, and a much smaller CVE surface), `apk --no-cache upgrade` to pick up OS fixes newer than the base tag, a fixed unprivileged `app` uid/gid `1001` (`USER app`, plays well with K8s `runAsNonRoot`), and `-XX:MaxRAMPercentage=75` so the JVM sizes its heap from the container's cgroup limit.

4. **Trivy image scan gates CI.** A new `image` matrix job (needs `backend`) builds each image with a GHA layer cache and scans it with Trivy, failing on **fixable** HIGH/CRITICAL. A short, commented `.trivyignore` holds consciously-accepted, time-boxed exceptions (currently the pgjdbc `CVE-2026-54291`, pending a Boot-BOM bump to 42.7.12) so the gate stays strict without blocking on a cross-cutting dependency change.

## Consequences

- **Positive:** deploy-ready images; fast rebuilds (dependency layers cached in-image and in GHA); a security floor enforced on every PR; a single template the other three services drop straight into (`matrix.service` grows by one line each).
- **Negative / cost:** the repo-root build context couples the image to the monorepo layout (mitigated by `.dockerignore`); `apk upgrade` trades a little reproducibility for fewer CVEs; the `.trivyignore` must be pruned or it silently rots (each entry carries a removal condition); no image is **pushed** yet — publishing to a registry (Artifact Registry) and image signing come with the GKE work.
- **Revisit** with: registry push + tagging on release, health-based `HEALTHCHECK`/K8s probes (`/actuator/health/{liveness,readiness}`), SBOM generation, and — if start-up/size matters more — a `jlink` custom runtime or distroless base.
