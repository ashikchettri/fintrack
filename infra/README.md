# infra

- `postgres/init/` — first-boot schema creation for the Compose database
- `k8s/` — raw Kubernetes manifests (phase 5), converted to a Helm chart afterwards
- Terraform for GCP (phase 6 stretch)

Root `docker-compose.yml` is the local dev environment.
