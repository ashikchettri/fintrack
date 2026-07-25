output "artifact_registry" {
  description = "Docker image prefix — set as image.registry in the Helm chart."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.fintrack.repository_id}"
}

output "cluster_name" {
  value = google_container_cluster.fintrack.name
}

output "cluster_location" {
  value = google_container_cluster.fintrack.location
}

output "cloudsql_connection_name" {
  description = "INSTANCE connection name for the Cloud SQL Auth Proxy."
  value       = google_sql_database_instance.fintrack.connection_name
}

output "app_service_account" {
  description = "Annotate the app KSA with iam.gke.io/gcp-service-account = this."
  value       = google_service_account.app.email
}

output "deployer_service_account" {
  value = google_service_account.deployer.email
}

output "wif_provider" {
  description = "workload_identity_provider for the deploy workflow's google-github-actions/auth."
  value       = google_iam_workload_identity_pool_provider.github.name
}
