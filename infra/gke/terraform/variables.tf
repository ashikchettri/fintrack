variable "project_id" {
  description = "Target GCP project. MUST be a dedicated FinTrack project — never a shared or production project for something else."
  type        = string
}

variable "region" {
  description = "Region for the cluster, Artifact Registry and Cloud SQL."
  type        = string
  default     = "australia-southeast1"
}

variable "cluster_name" {
  description = "GKE Autopilot cluster name."
  type        = string
  default     = "fintrack"
}

variable "db_tier" {
  description = "Cloud SQL machine tier (1 vCPU / 3.75 GB by default)."
  type        = string
  default     = "db-custom-1-3840"
}

variable "github_repo" {
  description = "owner/repo permitted to deploy via Workload Identity Federation."
  type        = string
  default     = "ashikchettri/fintrack"
}

variable "namespace" {
  description = "Kubernetes namespace the app runs in."
  type        = string
  default     = "fintrack"
}

variable "app_ksa" {
  description = "Kubernetes ServiceAccount the app pods use (bound to the app GSA via Workload Identity)."
  type        = string
  default     = "fintrack"
}
