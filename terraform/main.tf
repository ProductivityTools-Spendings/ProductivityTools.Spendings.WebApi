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
  # familyexpenses-webapi service) is shipped to Cloud Logging.
  ops_agent_setup = file("${path.module}/scripts/install-ops-agent.sh")
}

# Identity for the API VM so the Ops Agent can ship logs and metrics to Cloud Logging / Monitoring.
resource "google_service_account" "familyexpenses_vm" {
  account_id   = "familyexpenses-vm"
  display_name = "FamilyExpenses API VM (Ops Agent)"
}

resource "google_project_iam_member" "familyexpenses_vm_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.familyexpenses_vm.email}"
}

resource "google_project_iam_member" "familyexpenses_vm_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.familyexpenses_vm.email}"
}

# 1. Main VPC (Custom Subnet Mode)
resource "google_compute_network" "vpc_network" {
  name                    = var.network_name
  auto_create_subnetworks = false
  description             = "VPC network for FamilyExpenses environment"
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

# 3. Network Firewall Policy: familyexpenses-basic-access
resource "google_compute_network_firewall_policy" "familyexpenses_basic_access" {
  name        = "familyexpenses-basic-access"
  description = "Network firewall policy for FamilyExpenses environment"
}

# Associate the Firewall Policy with the VPC Network
resource "google_compute_network_firewall_policy_association" "familyexpenses_policy_assoc" {
  name              = "familyexpenses-basic-access-assoc"
  attachment_target = google_compute_network.vpc_network.id
  firewall_policy   = google_compute_network_firewall_policy.familyexpenses_basic_access.name
}

# Rule within the Firewall Policy allowing public HTTP traffic to FamilyExpenses WebApi (port 8086)
resource "google_compute_network_firewall_policy_rule" "allow_http" {
  firewall_policy = google_compute_network_firewall_policy.familyexpenses_basic_access.name
  description     = "Allows incoming HTTP traffic on port 8086 (FamilyExpenses WebApi)"
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
  firewall_policy = google_compute_network_firewall_policy.familyexpenses_basic_access.name
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

# Pre-reserved Static External IP for FamilyExpenses WebApi
data "google_compute_address" "familyexpenses_webapi_ip" {
  name   = var.familyexpenses_webapi_instance_name
  region = var.region
}

# 4. Virtual Machine in the Warsaw subnetwork for FamilyExpenses WebApi
resource "google_compute_instance" "familyexpenses_webapi_vm" {
  name         = var.familyexpenses_webapi_instance_name
  machine_type = var.familyexpenses_webapi_machine_type
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
      nat_ip = data.google_compute_address.familyexpenses_webapi_ip.address
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
    mkdir -p /opt/familyexpenses-webapi
    chmod 777 /opt/familyexpenses-webapi

    # 3. Setup GitHub Actions Self-Hosted Runner if credentials are provided
    REPO="${var.familyexpenses_webapi_github_repo}"
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
        ./config.sh --url "https://github.com/$${REPO}" --token "$${REG_TOKEN}" --name "${var.familyexpenses_webapi_instance_name}" --labels "${var.familyexpenses_webapi_instance_name}" --unattended --replace
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
    email  = google_service_account.familyexpenses_vm.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  # Lets Terraform stop the VM when a change requires it instead of failing the apply.
  allow_stopping_for_update = true
}
