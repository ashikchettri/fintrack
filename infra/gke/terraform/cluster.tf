# GKE Autopilot — Google manages nodes, and Workload Identity is on by default,
# so app pods authenticate to Google APIs as a GSA with no node keys.
resource "google_container_cluster" "fintrack" {
  name     = var.cluster_name
  location = var.region

  enable_autopilot = true

  release_channel {
    channel = "REGULAR"
  }

  # Dev default. Set true for a real production cluster.
  deletion_protection = false

  depends_on = [google_project_service.enabled]
}

# Docker image repository (services push/pull here).
resource "google_artifact_registry_repository" "fintrack" {
  location      = var.region
  repository_id = "fintrack"
  format        = "DOCKER"
  description   = "FinTrack service images"
  depends_on    = [google_project_service.enabled]
}
