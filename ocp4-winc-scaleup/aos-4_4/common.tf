# flexy installation job number
variable "BUILD_NUMBER" {
  type = string
}

# rhel_worker things
variable "RHEL_WORKER_COUNT" {
  type    = string
  default = "1"
}

variable "PLATFORM" {
  type = string
}

# big map for platforms 
variable "EXTRA_VARS" {
  type = string
}

variable "CLUSTER_INFO_PATH" {
  type = string
}

# shared variables for platforms, openstack, gcp, vsphere, aws
locals {
  cluster_info = yamldecode(file("${var.CLUSTER_INFO_PATH}/cluster_info.yaml"))
  default_map  = yamldecode(file(join("/", [path.root, "default_map.yaml"])))
  extra_vars   = yamldecode(join("\n", [var.EXTRA_VARS, "placehoder: true"]))

  platforms = {
    for p in ["aws", "gce", "openstack", "vsphere"] :
    p => merge(
      lookup(local.default_map, p, {}),
      lookup(local.extra_vars, p, {}),
    )
  }

  rhel_properties = merge(
    local.default_map,
    local.extra_vars,
    local.platforms
  )

  RHEL_WORKER_PREFIX = local.rhel_properties.RHEL_WORKER_PREFIX
  kubeconfig_url     = local.cluster_info.INSTALLER.KUBECONFIG_URL
  metadata           = local.cluster_info.INSTALLER.METADATA
  cluster_name       = local.metadata.clusterName
  cluster_domain     = local.rhel_properties.cluster_domain

  # flexy jenkins will generate the infra_id for all type installation
  infra_id      = trimspace(local.cluster_info.INSTALLER.INFRA_ID)
  network_type  = local.cluster_info.GENERAL.NETWORK_TYPE
  platform_dict = lookup(local.rhel_properties, var.PLATFORM, null)
}

# outputs variables for scaleup runner
# local value rhel_worker_list should be defined within platform
output "RHEL_WORKER_LIST" {
  value = join(",", local.rhel_worker_list)
}

output "CLUSTER_KUBECONFIG_LOCATION" {
  value = local.kubeconfig_url
}

output "ANSIBLE_USER" {
  value = local.platform_dict.ssh_user
}
