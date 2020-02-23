locals {
  rhel_worker_list = aws_cloudformation_stack.rhel_stack.*.outputs.PrivateDnsName
}

