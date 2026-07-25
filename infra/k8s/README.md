# FinTrack on Kubernetes

Manifests to run FinTrack in-cluster (Minikube today; GKE later). Kustomize-based,
one namespace (`fintrack`). See **ADR 016** for the design.

## What's here

The whole stack: foundation + backing stores + **all four services** + an
**Ingress** fronting the gateway. auth-service is the reference Deployment;
finance/gateway/insight follow the same pattern.

```
namespace • config (ConfigMap + dev Secret) • postgres (StatefulSet + PVC + schema init)
• redis • mailpit • auth / finance / gateway / insight (Deployment + Service each)
• ingress → gateway (single public entry, ADR 007)
```

Every workload runs **non-root** with dropped capabilities, resource
requests/limits, and — for the app — a read-only root filesystem and
liveness/readiness/startup probes on `/actuator/health/*`.

## Deploy to Minikube

```bash
minikube start                       # any driver; the ingress addon is handy later

# 1. Build each service image and load it into the cluster (no registry yet)
for s in auth-service finance-service gateway-service insight-service; do
  docker build -f services/$s/Dockerfile -t fintrack/$s:latest .
  minikube image load fintrack/$s:latest
done

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

Everything enters through the gateway via the Ingress:

```bash
echo "$(minikube ip) fintrack.local" | sudo tee -a /etc/hosts
curl -s http://fintrack.local/actuator/health                 # gateway health, {"status":"UP"}
curl -s -XPOST http://fintrack.local/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"x@y.z","password":"bad"}'   # -> 401 via gateway→auth
```

If the qemu driver's node IP isn't reachable from the host, tunnel the nginx
controller instead:

```bash
kubectl -n ingress-nginx port-forward svc/ingress-nginx-controller 8080:80
curl -s -H 'Host: fintrack.local' http://localhost:8080/actuator/health
```

Individual services / the mail inbox are still reachable directly if needed:

```bash
kubectl -n fintrack port-forward svc/auth-service 8081:8081   # /.well-known/jwks.json, /actuator/health
kubectl -n fintrack port-forward svc/mailpit 8025:8025        # verification-email inbox
```

## Notes / not-yet

- **Secrets.** `config/secret.yaml` holds only the throwaway dev Postgres password
  (matches docker-compose). Real environments create secrets out-of-band
  (Secret Manager / External Secrets) — never commit them.
- **TLS.** Minikube serves plain HTTP, so `FINTRACK_AUTH_REFRESH_COOKIE_SECURE`
  is `false` here (ADR 003). Behind TLS termination set it to `true`.
- **Images.** `imagePullPolicy: IfNotPresent` + `:latest` for locally-loaded
  images. Registry push + immutable tags come with the GKE work.
- **Next:** a Helm chart / Kustomize overlays for GKE (Cloud SQL instead of the
  in-cluster Postgres, Secret Manager + Workload Identity, managed TLS on the
  Ingress).
