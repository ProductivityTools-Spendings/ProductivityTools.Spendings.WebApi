output "network_id" {
  description = "ID of the created VPC network"
  value       = google_compute_network.vpc_network.id
}

output "network_name" {
  description = "Name of the created VPC network"
  value       = google_compute_network.vpc_network.name
}

output "subnetwork_id" {
  description = "ID of the subnetwork in Warsaw"
  value       = google_compute_subnetwork.subnet.id
}

output "subnetwork_name" {
  description = "Name of the subnetwork in Warsaw"
  value       = google_compute_subnetwork.subnet.name
}

output "subnetwork_region" {
  description = "Region of the subnetwork"
  value       = google_compute_subnetwork.subnet.region
}

output "firewall_policy_id" {
  description = "ID of the created Network Firewall Policy"
  value       = google_compute_network_firewall_policy.familyexpenses_basic_access.id
}

output "firewall_policy_name" {
  description = "Name of the created Network Firewall Policy"
  value       = google_compute_network_firewall_policy.familyexpenses_basic_access.name
}

output "familyexpenses_webapi_instance_name" {
  description = "Name of the FamilyExpenses WebApi virtual machine"
  value       = google_compute_instance.familyexpenses_webapi_vm.name
}

output "familyexpenses_webapi_instance_internal_ip" {
  description = "Internal IP address of the FamilyExpenses WebApi virtual machine"
  value       = google_compute_instance.familyexpenses_webapi_vm.network_interface[0].network_ip
}

output "familyexpenses_webapi_instance_external_ip" {
  description = "Public external IP address of the FamilyExpenses WebApi virtual machine"
  value       = google_compute_instance.familyexpenses_webapi_vm.network_interface[0].access_config[0].nat_ip
}

output "familyexpenses_webapi_instance_web_url" {
  description = "URL to access the FamilyExpenses WebApi debug endpoint"
  value       = "http://${google_compute_instance.familyexpenses_webapi_vm.network_interface[0].access_config[0].nat_ip}:8086/api/debug/hello"
}
