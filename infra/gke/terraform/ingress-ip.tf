# Reserved global IP for the GKE Ingress load balancer — a stable target for the
# domain's DNS A record (and so the managed TLS cert can provision). Reference
# its name as ingress.staticIpName in the Helm chart.
resource "google_compute_global_address" "ingress" {
  name       = "fintrack-ip"
  depends_on = [google_project_service.enabled]
}
