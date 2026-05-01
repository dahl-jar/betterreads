output "instance_public_ip" {
  description = "Ephemeral public IPv4 address attached to the compute instance. Lost on stop, reassigned on start."
  value       = oci_core_instance.app.public_ip
}

output "instance_private_ip" {
  description = "Private IPv4 address inside the VCN."
  value       = oci_core_instance.app.private_ip
}

output "ssh_command" {
  description = "Ready-to-paste SSH command. Replace the username if you provisioned a non-Ubuntu image (Oracle Linux uses 'opc')."
  value       = "ssh ubuntu@${oci_core_instance.app.public_ip}"
}

output "object_storage_bucket" {
  description = "Object Storage bucket name."
  value       = oci_objectstorage_bucket.artifacts.name
}

output "object_storage_namespace" {
  description = "Object Storage namespace (tenancy-scoped, immutable)."
  value       = data.oci_objectstorage_namespace.tenancy.namespace
}

output "vcn_id" {
  description = "OCID of the VCN. Useful for nested modules or peering setups later."
  value       = oci_core_vcn.main.id
}

output "subnet_id" {
  description = "OCID of the public subnet."
  value       = oci_core_subnet.public.id
}
