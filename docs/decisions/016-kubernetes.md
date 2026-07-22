# ADR 016 — Kubernetes manifests (Minikube → GKE)

**Status:** Accepted · 2026-07-22

## Context

With every service containerized (ADR 015), the next milestone is running FinTrack on Kubernetes — Minikube locally, GKE later. We need manifests that are declarative, secure by default, and uniform across the four services, without pulling in more tooling than warranted for the first slice. This ADR sets the structure and the conventions, established on the backing stores + **auth-service** as the reference application (the other services and the Ingress follow the same shape).

## Decision

1. **Plain manifests, assembled with Kustomize** (`infra/k8s/`, one `kustomization.yaml`), applied with `kubectl apply -k` — no Helm yet. Kustomize is built into kubectl, needs no extra install, and a single overlay-free base is enough until we have per-environment differences (GKE) worth templating. Everything lands in one `fintrack` namespace with a shared `part-of` label.

2. **Config vs secrets are split.** A `fintrack-config` ConfigMap holds all non-secret env (DB host, Redis host, mail host, active profile, cookie-secure flag), injected with `envFrom`. Secrets are separate: a dev-only `fintrack-secrets` (the throwaway Postgres password that already lives in docker-compose) committed for a frictionless local `apply`, and the **JWT signing key as its own `auth-jwt` secret created from a PEM file out-of-band** — a real private key never enters git (ADR 002). Real environments provide all secrets via Secret Manager / External Secrets.

3. **Postgres is a StatefulSet with a `volumeClaimTemplates` PVC**; the schema-per-service init SQL (ADR: schema isolation) ships as a ConfigMap mounted into `/docker-entrypoint-initdb.d`. Redis and Mailpit are plain Deployments. One shared Postgres instance keeps the schema-per-service model from ADR-era decisions.

4. **Hardened workloads.** Every pod is `runAsNonRoot` (the app at uid 1001, matching the Dockerfile), drops all capabilities, and disables privilege escalation; the app also runs with a **read-only root filesystem** (only an `emptyDir` `/tmp` is writable). Each container declares resource requests/limits. The app exposes **liveness/readiness/startup probes** on `/actuator/health/{liveness,readiness}` (Boot's Kubernetes probes), and an **init-container waits for Postgres** so a cold start doesn't crash-loop while Flyway waits for the DB.

5. **Local images, no registry yet.** `imagePullPolicy: IfNotPresent` with `:latest`, loaded via `minikube image load`. Registry push and immutable, release-tagged images come with the GKE work.

## Consequences

- **Positive:** `kubectl apply -k infra/k8s` brings up a hardened, probed stack that was validated end-to-end on Minikube (auth-service migrates its schema, loads its JWT key from the mounted secret, and serves JWKS + a healthy `/actuator/health`); the reference Deployment is a copy-paste template for the remaining services; security posture (non-root, read-only FS, limits, dropped caps) is set from day one.
- **Negative / cost:** the committed dev Secret is fine only for local Minikube and must never be reused; a single-replica Postgres StatefulSet is not HA (fine for dev, revisit for prod — Cloud SQL on GKE sidesteps it); no Helm means per-environment variation is manual until we add overlays or a chart; the app image is loaded by hand until a registry exists.
- **Revisit** with: the remaining service Deployments + an Ingress to the gateway, a Helm chart (or Kustomize overlays) for GKE, Cloud SQL instead of in-cluster Postgres, and secret delivery via Secret Manager + Workload Identity.
