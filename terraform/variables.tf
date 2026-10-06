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
  default     = "familyexpenses"
}

variable "subnet_cidr" {
  type        = string
  description = "CIDR IP range for the Warsaw subnetwork"
  default     = "10.0.0.0/24"
}

variable "zone" {
  type        = string
  description = "GCP Zone for the virtual machine"
  default     = "europe-central2-a"
}

variable "familyexpenses_webapi_instance_name" {
  type        = string
  description = "Name of the FamilyExpenses WebApi virtual machine instance (and reserved static IP name)"
  default     = "familyexpenses-webapi"
}

variable "familyexpenses_webapi_machine_type" {
  type        = string
  description = "Machine type for the FamilyExpenses WebApi virtual machine"
  default     = "e2-medium"
}

variable "familyexpenses_webapi_github_repo" {
  type        = string
  description = "GitHub repository for FamilyExpenses WebApi in format owner/repo"
  default     = "ProductivityTools-FamilyExpenses/ProductivityTools.FamilyExpenses.WebApi"
}

variable "github_pat" {
  type        = string
  description = "GitHub Personal Access Token with repo administration/runner permissions"
  sensitive   = true
  default     = ""
}
