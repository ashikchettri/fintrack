# Guardrail against surprise bills: a monthly budget scoped to this project that
# emails the billing admins at 50/90/100% of the threshold. Doesn't cap spend —
# it warns. (Default recipients are the billing account admins.)
data "google_project" "this" {
  project_id = var.project_id
}

resource "google_billing_budget" "fintrack" {
  billing_account = var.billing_account
  display_name    = "FinTrack monthly budget"

  budget_filter {
    projects = ["projects/${data.google_project.this.number}"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = tostring(var.monthly_budget)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.9
  }
  threshold_rules {
    threshold_percent = 1.0
  }

  depends_on = [google_project_service.enabled]
}
