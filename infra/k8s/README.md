# FinTrack on Kubernetes

Manifests to run FinTrack in-cluster (Minikube today; GKE later). Kustomize-based,
one namespace (`fintrack`). See **ADR 016** for the design.

## What's here (first slice)

Foundation + backing stores + **auth-service** as the reference application
Deployment. finance-, gateway- and insight-service, plus the Ingress, follow the
same pattern and land in follow-up changes.

```
namespace • config (ConfigMap + dev Secret) • postgres (StatefulSet + PVC + schema init)
• redis • mailpit • auth-service (Deployment + Service)
```

Every workload runs **non-root** with dropped capabilities, resource
requests/limits, and — for the app — a read-only root filesystem and
liveness/readiness/startup probes on `/actuator/health/*`.

## Deploy to Minikube

```bash
minikube start                       # any driver; the ingress addon is handy later

# 1. Build the service image and load it into the cluster (no registry yet)
docker build -f services/auth-service/Dockerfile -t fintrack/auth-service:latest .
minikube image load fintrack/auth-service:latest

# 2. Create the JWT signing key secret (a real private key never lives in git).
#    Outside the `local` profile the app requires it (ADR 002).
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-signing.pem
kubectl create namespace fintrack --dry-run=client -o yaml | kubectl apply -f -
kubectl -n fintrack create secret generic auth-jwt --from-file=jwt-signing.pem

# 3. Apply everything
kubectl apply -k infra/k8s

# 4. Watch it come up
kubectl -n fintrack get pods -w
```

### Try it

```bash
kubectl -n fintrack port-forward svc/auth-service 8081:8081
curl -s localhost:8081/actuator/health            # {"status":"UP",...}
curl -s localhost:8081/.well-known/jwks.json      # the public verification key

kubectl -n fintrack port-forward svc/mailpit 8025:8025   # verification-email inbox
```

## Notes / not-yet

- **Secrets.** `config/secret.yaml` holds only the throwaway dev Postgres password
  (matches docker-compose). Real environments create secrets out-of-band
  (Secret Manager / External Secrets) — never commit them.
- **TLS.** Minikube serves plain HTTP, so `FINTRACK_AUTH_REFRESH_COOKIE_SECURE`
  is `false` here (ADR 003). Behind TLS termination set it to `true`.
- **Images.** `imagePullPolicy: IfNotPresent` + `:latest` for locally-loaded
  images. Registry push + immutable tags come with the GKE work.
- **Next:** finance/gateway/insight Deployments, an Ingress routing to the
  gateway, then a Helm chart.
