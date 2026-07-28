# The application's Google service account. The Kubernetes SA impersonates it via
# Workload Identity, so pods reach Cloud SQL / Secret Manager with no key files.
resource "google_service_account" "app" {
  account_id   = "fintrack-app"
  display_name = "FinTrack application"
}

resource "google_project_iam_member" "app" {
  for_each = toset([
    "roles/cloudsql.client",              # connect through the Cloud SQL Auth Proxy
    "roles/secretmanager.secretAccessor", # read runtime secrets
    "roles/artifactregistry.reader",      # pull images
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.app.email}"
}

# Bind the in-cluster KSA (namespace/app_ksa) to the app GSA. Depends on the
# cluster: the *.svc.id.goog identity pool only exists once it's created.
resource "google_service_account_iam_member" "app_workload_identity" {
  service_account_id = google_service_account.app.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[${var.namespace}/${var.app_ksa}]"
  depends_on         = [google_container_cluster.fintrack]
}
