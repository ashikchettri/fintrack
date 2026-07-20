# ADR 002 — JWT signing key management

**Status:** Accepted · 2026-07-12

## Context

auth-service signs access JWTs with RS256 so downstream services (finance-service, the gateway)
verify tokens via the public key from a JWKS endpoint without sharing secrets. The private
key has to come from somewhere in four environments (`local`, `docker`, `k8s`, `gcp`),
without violating "no secrets in git" and without adding local-dev setup friction.

## Decision

1. **One loading path everywhere**: if `fintrack.auth.jwt.private-key` is configured
   (a Spring `Resource` pointing at a PKCS#8 PEM), it is loaded — in every profile,
   including `local`.
2. **Ephemeral fallback only in `local`**: when no key is configured and the `local`
   profile is active, generate an in-memory RSA keypair at startup and log a warning.
   Access tokens die on restart; the DB-backed refresh flow reissues them, so the
   local-dev impact is one silent refresh.
3. **Fail fast everywhere else**: no key + not `local` = startup failure. A silently
   generated key in a multi-replica deployment would mean every pod signs with a
   different key and tokens randomly fail verification.
4. `spring.profiles.default: local` so bare `bootRun` and tests get the fallback without
   ceremony; real deployments always set `SPRING_PROFILES_ACTIVE` explicitly.
5. **`kid` is the RFC 7638 JWK thumbprint** of the key, computed — not configured. JWKS
   serves a key *list* so rotation (publish old + new, then drop old after the 15-min
   access-token TTL) needs no schema change later. Rotation itself is not implemented yet.

## Consequences

- Positive: zero-setup local dev; prod misconfiguration is a startup error, not a
  runtime mystery; key material never in git (gitleaks PR check also guards this);
  path to K8s Secrets / GCP Secret Manager is just "mount PEM, set property".
- Negative: two behaviors to reason about (load vs generate); local access tokens
  don't survive restarts.
- **Production hardening path (not now):** cloud KMS signing (e.g. GCP Cloud KMS),
  where the private key is unexportable and every signing call is audited. Revisit
  with the cloud/hardening work; would replace the PEM loading path behind the same interface.