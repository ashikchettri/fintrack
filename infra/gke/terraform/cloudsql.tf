# Managed Postgres (replaces the in-cluster StatefulSet). Reached from the app
# via the Cloud SQL Auth Proxy — IAM-authenticated, so no authorized networks
# and no password on the wire for the connection itself.
resource "google_sql_database_instance" "fintrack" {
  name             = "fintrack-pg"
  database_version = "POSTGRES_17"
  region           = var.region

  settings {
    tier              = var.db_tier
    availability_type = "ZONAL" # REGIONAL for HA in production
    disk_autoresize   = true

    backup_configuration {
      enabled = true
    }

    ip_configuration {
      ipv4_enabled = true
      # No authorized_networks: reachable only via the Cloud SQL Auth Proxy.
    }
  }

  # Dev default. Set true for production.
  deletion_protection = false
  depends_on          = [google_project_service.enabled]
}

resource "google_sql_database" "fintrack" {
  name     = "fintrack"
  instance = google_sql_database_instance.fintrack.name
}

# App DB password — generated here and kept only in Secret Manager (below).
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "google_sql_user" "fintrack" {
  name     = "fintrack"
  instance = google_sql_database_instance.fintrack.name
  password = random_password.db.result
}
