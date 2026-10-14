locals {
  service       = "os"
  path_prefix   = "/carworkshop/v1"
  rule_priority = 100

  db_identifier = "${var.project}-os-postgres"
  db_port       = 5432

  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Repo      = "fiap-fase4-os-service"
  }

  vpc_id          = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  private_subnets = split(",", nonsensitive(data.aws_ssm_parameter.private_subnets.value))
  eks_node_sg_id  = nonsensitive(data.aws_ssm_parameter.eks_node_sg_id.value)
  listener_arn    = nonsensitive(data.aws_ssm_parameter.alb_listener_arn.value)
}
