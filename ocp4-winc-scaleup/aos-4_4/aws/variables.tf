variable "AWS_CREDS" {
  type = string
}

locals {
  aws_region = local.metadata.aws.region
}

variable "rhel_stack_path" {
  type    = string
  default = "90_new_rhel_worker.yaml"
}

data "aws_cloudformation_stack" "cf_vpc" {
  name = "${local.cluster_name}-vpc"
}

locals {
  aws_rhel_properties  = local.rhel_properties.aws
  vpc_outputs          = data.aws_cloudformation_stack.cf_vpc.outputs
  workerprivatesubnets = local.vpc_outputs.PrivateSubnetIds
  rhel_ami             = local.aws_rhel_properties.image
}

variable "aws_capabilities" {
  type = list(string)
  default = [
    "CAPABILITY_NAMED_IAM",
  ]
}