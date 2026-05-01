data "oci_objectstorage_namespace" "tenancy" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "artifacts" {
  compartment_id = var.compartment_ocid
  namespace      = data.oci_objectstorage_namespace.tenancy.namespace
  name           = var.object_storage_bucket_name
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Disabled"
  freeform_tags  = var.tags
}
