# OCI Terraform

Always Free Oracle Cloud setup: one VCN, one public subnet, one Ampere ARM VM, one Object Storage bucket. Defaults stay inside Free Tier limits; the variable validation refuses values that don't.

## What you get

| Resource | Free Tier limit | Default |
|---|---|---|
| VCN + subnet + IGW | unlimited | one VCN, one public subnet |
| Compute (A1.Flex Ampere) | 4 OCPU + 24 GB RAM | one VM at 2 OCPU + 12 GB |
| Boot volume | 200 GB | 50 GB |
| Object Storage (Standard) | 20 GB | one empty bucket |
| Public IPv4 | ephemeral free, reserved paid | ephemeral |

## What's not here

NAT Gateway (paid), reserved public IP (paid), Load Balancer, Autonomous DB, File Storage. Add them yourself when you need them.

## Prerequisites

- OCI tenancy with `~/.oci/config` already working (run `oci setup config` if not).
- Terraform 1.5+.
- An SSH public key.

## Run

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# Fill in OCIDs, fingerprint, region, SSH public key

terraform init
terraform plan
terraform apply
```

Outputs include the public IP and a ready SSH command.

## Destroy

```bash
terraform destroy
```

## Tweaks

- **Bigger VM**: `instance_ocpus` up to 4, `instance_memory_gb` up to 24.
- **Multiple VMs**: wrap `oci_core_instance.app` in `count`. Totals stay under 4 OCPU + 24 GB.
- **Oracle Linux**: `instance_os = "Oracle Linux"`, `instance_os_version = "9"`. SSH user becomes `opc`.
- **Lock down SSH**: set `ssh_ingress_cidr` to `<your-ip>/32`.
- **Bigger boot volume**: `boot_volume_size_gb` up to 200.

## State

Local `terraform.tfstate`, gitignored. Fine for solo use. Migrate to OCI Object Storage later if you need shared state.

## Cost

Variable validation blocks the obvious mistakes (`ocpus = 40`). The `free-tier-guard` budget alert catches everything else within a day. Neither is a hard cap — there's no "never charge me" toggle on OCI.
