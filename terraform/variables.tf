variable "project_id" {
  type        = string
  description = "Google Cloud Project ID"
  default     = "pwujczyk-pt"
}

variable "region" {
  type        = string
  description = "GCP Region for the subnetwork"
  default     = "europe-central2"
}

variable "network_name" {
  type        = string
  description = "Name of the VPC network"
  default     = "spendings"
}

variable "subnet_cidr" {
  type        = string
  description = "CIDR IP range for the Warsaw subnetwork"
  default     = "10.10.0.0/24"
}

variable "zone" {
  type        = string
  description = "GCP Zone for the virtual machine"
  default     = "europe-central2-a"
}

variable "spendings_webapi_instance_name" {
  type        = string
  description = "Name of the Spendings WebApi virtual machine instance (and reserved static IP name)"
  default     = "spendings-webapi"
}

variable "spendings_webapi_machine_type" {
  type        = string
  description = "Machine type for the Spendings WebApi virtual machine"
  default     = "e2-medium"
}

variable "spendings_webapi_github_repo" {
  type        = string
  description = "GitHub repository for Spendings WebApi in format owner/repo"
  default     = "ProductivityTools-Spendings/ProductivityTools.Spendings.WebApi"
}

variable "github_pat" {
  type        = string
  description = "GitHub Personal Access Token with repo administration/runner permissions"
  sensitive   = true
  default     = ""
}

variable "db_password" {
  type        = string
  description = "Password for the PostgreSQL database user"
  sensitive   = true
  default     = "Pawel123"
}
