provider "aws" {
  shared_credentials_file = var.AWS_CREDS
  region                  = local.aws_region
  profile                 = "default"
}

data "aws_security_group" "worker" {
  tags = {
    Name = "${local.infra_id}-worker-sg"
  }
}

resource "aws_cloudformation_stack" "rhel_stack" {
  count = var.RHEL_WORKER_COUNT

  name = "${local.cluster_name}-${local.RHEL_WORKER_PREFIX}-${count.index}"
  parameters = {
    InfrastructureName        = local.infra_id
    WorkerSubnet              = element(split(",", local.workerprivatesubnets), 0)
    WorkerSecurityGroupId     = data.aws_security_group.worker.id
    WorkerInstanceProfileName = "${local.infra_id}-worker-profile"
    RhelAmi                   = local.rhel_ami
    InstanceNameSuffix        = "${local.RHEL_WORKER_PREFIX}-${count.index}"
  }

  template_body = file(join("/", [path.root, var.rhel_stack_path]))
  capabilities  = var.aws_capabilities
}

