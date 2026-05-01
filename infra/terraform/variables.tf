variable "tenancy_ocid" {
  description = "OCID of the tenancy. Find it in the OCI Console under Tenancy details."
  type        = string
}

variable "user_ocid" {
  description = "OCID of the IAM user Terraform authenticates as. Find it under My Profile."
  type        = string
}

variable "api_key_fingerprint" {
  description = "Fingerprint of the API signing key uploaded to the user's API Keys page."
  type        = string
}

variable "api_private_key_path" {
  description = "Absolute path to the API signing private key (PEM)."
  type        = string
  default     = "~/.oci/oci_api_key.pem"
}

variable "region" {
  description = "OCI region key (e.g. eu-amsterdam-1, eu-stockholm-1, us-ashburn-1)."
  type        = string
}

variable "compartment_ocid" {
  description = "OCID of the compartment to provision into. Use the tenancy OCID for the root compartment."
  type        = string
}

variable "project_name" {
  description = "Short identifier used as a prefix on resource display names. Lowercase, no spaces."
  type        = string
  default     = "app"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.project_name))
    error_message = "project_name must be 2-21 chars, lowercase letters, digits, and hyphens, starting with a letter."
  }
}

variable "ssh_public_key" {
  description = "SSH public key content (not a path) authorized on the compute instance. Run: cat ~/.ssh/id_ed25519.pub"
  type        = string
}

variable "vcn_cidr" {
  description = "CIDR block for the VCN. Default leaves room for multiple subnets."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for the public subnet inside the VCN."
  type        = string
  default     = "10.0.1.0/24"
}

variable "instance_shape" {
  description = "Compute shape. Always Free options: VM.Standard.A1.Flex (ARM, recommended) or VM.Standard.E2.1.Micro (AMD, 1GB RAM, barely usable)."
  type        = string
  default     = "VM.Standard.A1.Flex"

  validation {
    condition     = contains(["VM.Standard.A1.Flex", "VM.Standard.E2.1.Micro"], var.instance_shape)
    error_message = "instance_shape must be an Always Free shape: VM.Standard.A1.Flex or VM.Standard.E2.1.Micro."
  }
}

variable "instance_ocpus" {
  description = "OCPUs for the Ampere VM. Free Tier total cap is 4 OCPU across all A1.Flex instances."
  type        = number
  default     = 2

  validation {
    condition     = var.instance_ocpus >= 1 && var.instance_ocpus <= 4
    error_message = "instance_ocpus must be between 1 and 4 (Always Free cap)."
  }
}

variable "instance_memory_gb" {
  description = "Memory in GB for the Ampere VM. Free Tier total cap is 24 GB across all A1.Flex instances. Rule of thumb: 6 GB per OCPU."
  type        = number
  default     = 12

  validation {
    condition     = var.instance_memory_gb >= 6 && var.instance_memory_gb <= 24
    error_message = "instance_memory_gb must be between 6 and 24 (Always Free cap)."
  }
}

variable "boot_volume_size_gb" {
  description = "Boot volume size in GB. Free Tier total block storage cap is 200 GB across all volumes."
  type        = number
  default     = 50

  validation {
    condition     = var.boot_volume_size_gb >= 50 && var.boot_volume_size_gb <= 200
    error_message = "boot_volume_size_gb must be between 50 (OCI minimum) and 200 (Always Free cap)."
  }
}

variable "instance_os" {
  description = "Operating system family for the boot image. Common Free Tier choices: 'Canonical Ubuntu' or 'Oracle Linux'."
  type        = string
  default     = "Canonical Ubuntu"
}

variable "instance_os_version" {
  description = "OS version. Examples: '22.04' for Ubuntu, '9' for Oracle Linux."
  type        = string
  default     = "22.04"
}

variable "object_storage_bucket_name" {
  description = "Name for the Object Storage bucket. Must be unique within your tenancy namespace."
  type        = string
  default     = "app-artifacts"
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to SSH (port 22) into the instance. Default 0.0.0.0/0 is open to the internet — narrow to your home/office IP for production."
  type        = string
  default     = "0.0.0.0/0"
}

variable "tags" {
  description = "Freeform tags applied to every resource."
  type        = map(string)
  default = {
    managed-by = "terraform"
    tier       = "always-free"
  }
}
