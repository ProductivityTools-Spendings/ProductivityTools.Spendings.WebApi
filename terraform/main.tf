terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# Enable Cloud Identity-Aware Proxy (IAP) API for browser-based SSH access
resource "google_project_service" "iap" {
  service            = "iap.googleapis.com"
  disable_on_destroy = false
}

# Enable Cloud SQL Admin API
resource "google_project_service" "sqladmin" {
  service            = "sqladmin.googleapis.com"
  disable_on_destroy = false
}

# Enable Cloud Logging / Monitoring so the Ops Agent on the VM can ship logs and metrics
resource "google_project_service" "logging" {
  service            = "logging.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "monitoring" {
  service            = "monitoring.googleapis.com"
  disable_on_destroy = false
}

locals {
  # Installs the Ops Agent so journald (and with it the Spring Boot logs of the
  # spendings-webapi service) is shipped to Cloud Logging.
  ops_agent_setup = file("${path.module}/scripts/install-ops-agent.sh")
}

# Identity for the API VM so the Ops Agent can ship logs and metrics to Cloud Logging / Monitoring.
resource "google_service_account" "spendings_vm" {
  account_id   = "spendings-vm"
  display_name = "Spendings API VM (Ops Agent)"
}

resource "google_project_iam_member" "spendings_vm_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.spendings_vm.email}"
}

resource "google_project_iam_member" "spendings_vm_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.spendings_vm.email}"
}

# 1. Main VPC (Custom Subnet Mode)
resource "google_compute_network" "vpc_network" {
  name                    = var.network_name
  auto_create_subnetworks = false
  description             = "VPC network for Spendings environment"
}

# 2. Subnetwork in Warsaw region (europe-central2)
resource "google_compute_subnetwork" "subnet" {
  name                     = "${var.network_name}-subnet"
  ip_cidr_range            = var.subnet_cidr
  region                   = var.region
  network                  = google_compute_network.vpc_network.id
  private_ip_google_access = true
  description              = "Subnetwork located in Warsaw region (europe-central2)"
}

# 3. Network Firewall Policy: spendings-basic-access
resource "google_compute_network_firewall_policy" "spendings_basic_access" {
  name        = "spendings-basic-access"
  description = "Network firewall policy for Spendings environment"
}

# Associate the Firewall Policy with the VPC Network
resource "google_compute_network_firewall_policy_association" "spendings_policy_assoc" {
  name              = "spendings-basic-access-assoc"
  attachment_target = google_compute_network.vpc_network.id
  firewall_policy   = google_compute_network_firewall_policy.spendings_basic_access.name
}

# Rule within the Firewall Policy allowing public HTTP traffic to Spendings WebApi (port 8086)
resource "google_compute_network_firewall_policy_rule" "allow_http" {
  firewall_policy = google_compute_network_firewall_policy.spendings_basic_access.name
  description     = "Allows incoming HTTP traffic on port 8086 (Spendings WebApi)"
  priority        = 1000
  direction       = "INGRESS"
  action          = "allow"
  rule_name       = "allow-http-8086"

  match {
    src_ip_ranges = ["0.0.0.0/0"]
    layer4_configs {
      ip_protocol = "tcp"
      ports       = ["8086"]
    }
  }
}

# Rule within the Firewall Policy allowing SSH (TCP port 22) from IAP and any source
resource "google_compute_network_firewall_policy_rule" "allow_ssh" {
  firewall_policy = google_compute_network_firewall_policy.spendings_basic_access.name
  description     = "Allows SSH traffic including browser-based SSH via Google Cloud IAP"
  priority        = 1001
  direction       = "INGRESS"
  action          = "allow"
  rule_name       = "allow-ssh"

  match {
    src_ip_ranges = [
      "35.235.240.0/20", # Google Cloud Identity-Aware Proxy (IAP) CIDR used by Cloud Console Browser SSH
      "0.0.0.0/0"
    ]
    layer4_configs {
      ip_protocol = "tcp"
      ports       = ["22"]
    }
  }
}

# Pre-reserved Static External IP for Spendings WebApi (`34.116.163.207`)
data "google_compute_address" "spendings_webapi_ip" {
  name   = "spendings-webapi"
  region = var.region
}

# 4. Virtual Machine in the Warsaw subnetwork for Spendings WebApi
resource "google_compute_instance" "spendings_webapi_vm" {
  name         = var.spendings_webapi_instance_name
  machine_type = var.spendings_webapi_machine_type
  zone         = var.zone

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = 20
      type  = "pd-balanced"
    }
  }

  network_interface {
    subnetwork = google_compute_subnetwork.subnet.id

    access_config {
      nat_ip = data.google_compute_address.spendings_webapi_ip.address
    }
  }

  metadata = {
    enable-oslogin = "FALSE"
  }

  # Startup script: installs JDK 21, dependencies, and automatically configures GitHub Self-Hosted Runner
  metadata_startup_script = <<-EOF
    #!/bin/bash
    set -e

    # 1. Install prerequisites & Java 21 (Adoptium Temurin 21)
    apt-get update
    apt-get install -y curl jq git wget tar sudo ca-certificates gnupg

    mkdir -p /etc/apt/keyrings
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor --yes -o /etc/apt/keyrings/adoptium.gpg
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" > /etc/apt/sources.list.d/adoptium.list
    apt-get update
    apt-get install -y temurin-21-jdk || apt-get install -y -t bookworm-backports openjdk-21-jdk

    # 2. Prepare application directory
    mkdir -p /opt/spendings-webapi
    chmod 777 /opt/spendings-webapi

    # 3. Setup GitHub Actions Self-Hosted Runner if credentials are provided
    REPO="${var.spendings_webapi_github_repo}"
    PAT="${var.github_pat}"

    if [ -n "$REPO" ] && [ -n "$PAT" ]; then
      echo "Setting up GitHub Actions Runner for $REPO..."
      mkdir -p /opt/actions-runner
      cd /opt/actions-runner

      # Download runner package
      RUNNER_VERSION=$(curl -s https://api.github.com/repos/actions/runner/releases/latest | jq -r .tag_name | sed 's/v//')
      RUNNER_VERSION="$${RUNNER_VERSION:-2.317.0}"

      curl -o actions-runner-linux-x64.tar.gz -L "https://github.com/actions/runner/releases/download/v$${RUNNER_VERSION}/actions-runner-linux-x64-$${RUNNER_VERSION}.tar.gz"
      tar xzf ./actions-runner-linux-x64.tar.gz

      # Obtain registration token from GitHub API
      REG_TOKEN=$(curl -sX POST \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer $${PAT}" \
        "https://api.github.com/repos/$${REPO}/actions/runners/registration-token" | jq -r .token)

      if [ -n "$REG_TOKEN" ] && [ "$REG_TOKEN" != "null" ]; then
        export RUNNER_ALLOW_RUNASROOT="1"
        ./config.sh --url "https://github.com/$${REPO}" --token "$${REG_TOKEN}" --name "${var.spendings_webapi_instance_name}" --labels "${var.spendings_webapi_instance_name}" --unattended --replace
        ./svc.sh install root
        ./svc.sh start
        echo "GitHub Actions Runner registered and running as a systemd service!"
      else
        echo "Error: Could not retrieve registration token from GitHub API."
      fi
    fi

    # 4. Ship logs to Cloud Logging
    ${local.ops_agent_setup}
  EOF

  # Identity the Ops Agent uses to authenticate against Cloud Logging / Monitoring.
  service_account {
    email  = google_service_account.spendings_vm.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  # Lets Terraform stop the VM when a change requires it instead of failing the apply.
  allow_stopping_for_update = true
}

# 5. Cloud SQL PostgreSQL Instance with Private Service Connect (PSC) enabled
resource "google_sql_database_instance" "postgres" {
  name                = var.db_instance_name
  database_version    = var.db_version
  region              = var.region
  deletion_protection = false

  settings {
    tier = var.db_tier

    ip_configuration {
      ipv4_enabled = false

      psc_config {
        psc_enabled               = true
        allowed_consumer_projects = [var.project_id]
      }
    }

    backup_configuration {
      enabled = false
    }
  }

  depends_on = [
    google_project_service.sqladmin
  ]
}

# Spendings WebApi Database inside Cloud SQL
resource "google_sql_database" "spendings_webapi_database" {
  name     = var.spendings_webapi_db_name
  instance = google_sql_database_instance.postgres.name
}

# PostgreSQL Database User
resource "google_sql_user" "db_user" {
  name            = var.db_user
  instance        = google_sql_database_instance.postgres.name
  password        = var.db_password
  deletion_policy = "ABANDON"
}

# 6. Private Service Connect (PSC) Endpoint in consumer Subnetwork
# Internal IP reserved for the PSC endpoint within the Warsaw subnet
resource "google_compute_address" "db_psc_ip" {
  name         = "spendings-db-psc-ip"
  subnetwork   = google_compute_subnetwork.subnet.id
  address_type = "INTERNAL"
  region       = var.region
}

# Forwarding rule pointing to the Cloud SQL PSC Service Attachment
resource "google_compute_forwarding_rule" "db_psc_endpoint" {
  name                  = "spendings-db-psc-endpoint"
  region                = var.region
  network               = google_compute_network.vpc_network.id
  subnetwork            = google_compute_subnetwork.subnet.id
  ip_address            = google_compute_address.db_psc_ip.self_link
  target                = google_sql_database_instance.postgres.psc_service_attachment_link
  load_balancing_scheme = ""
}
