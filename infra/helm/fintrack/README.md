# FinTrack Helm chart

Deploys the whole FinTrack stack — Postgres, Redis, Mailpit, the four services
(auth / finance / gateway / insight), and an Ingress fronting the gateway. The
parameterized successor to the raw manifests in `infra/k8s/` (ADR 016); GKE
overlays live in `values-*.yaml`.

## Install (Minikube)

```bash
# Images: build + load each into the cluster (no registry locally)
for s in auth-service finance-service gateway-service insight-service; do
  docker build -f services/$s/Dockerfile -t fintrack/$s:latest .
  minikube image load fintrack/$s:latest
done

# The JWT signing key is created out-of-band (never in the chart) — ADR 002
kubectl create namespace fintrack
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-signing.pem
kubectl -n fintrack create secret generic auth-jwt --from-file=jwt-signing.pem

helm install fintrack infra/helm/fintrack -n fintrack
kubectl -n fintrack get pods -w
```

Then, as with the raw manifests:

```bash
echo "$(minikube ip) fintrack.local" | sudo tee -a /etc/hosts
curl -s http://fintrack.local/actuator/health          # {"status":"UP"}
```

## Key values

| Key | Default | Notes |
|-----|---------|-------|
| `image.registry` / `.repository` / `.tag` | `""` / `fintrack` / `latest` | image is `[registry/]repository/<service>:tag` |
| `config.springProfile` | `k8s` | `SPRING_PROFILES_ACTIVE` |
| `config.refreshCookieSecure` | `"false"` | set `"true"` behind TLS |
| `secrets.existingSecret` | `""` | bring your own Secret; else a dev one is rendered |
| `secrets.postgresPassword` / `.anthropicApiKey` | dev / empty | override for real envs |
| `postgres.enabled` / `redis.enabled` / `mailpit.enabled` | `true` | disable to use managed/external (e.g. Cloud SQL) |
| `services.<name>` | see `values.yaml` | `port`, `replicas`, `resources`, `waitForPostgres`, `jwtKey`, `anthropic`, `env` |
| `ingress.*` | `nginx` / `fintrack.local` | host + annotations |

## GKE

Override with `-f values-gke.example.yaml`: registry-hosted images, a real
Secret (`secrets.existingSecret`), TLS-safe cookies, and **Cloud SQL** instead
of the in-cluster Postgres.

Cloud SQL is reached via a **native-sidecar Cloud SQL Auth Proxy** — set
`cloudSqlProxy.enabled=true` + `cloudSqlProxy.connectionName` (and
`postgres.enabled=false`). For DB services the chart injects the proxy and points
`DB_HOST` at `127.0.0.1`, so the app connects over localhost with no DB creds on
the wire. Authentication is via **Workload Identity**: annotate the KSA with the
app GSA through `serviceAccount.annotations` (`iam.gke.io/gcp-service-account`).
Provision the cluster, Cloud SQL, registry and IAM with `infra/gke/terraform`
(ADR 017). Managed TLS on the Ingress is the remaining GKE step.
