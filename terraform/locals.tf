locals {
  service       = "os"
  path_prefix   = "/carworkshop/v1"
  rule_priority = 100

  db_identifier = "${var.project}-os-postgres"
  db_port       = 5432

  saga_replies_queue_name = "os-saga-replies.fifo"
  saga_replies_dlq_name   = "os-saga-replies-dlq.fifo"

  visibility_timeout_seconds = 30
  receive_wait_time_seconds  = 20
  max_receive_count          = 3
  dlq_retention_seconds      = 1209600

  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Repo      = "fiap-fase4-os-service"
  }

  vpc_id          = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  private_subnets = split(",", nonsensitive(data.aws_ssm_parameter.private_subnets.value))
  eks_node_sg_id  = nonsensitive(data.aws_ssm_parameter.eks_node_sg_id.value)
  listener_arn    = nonsensitive(data.aws_ssm_parameter.alb_listener_arn.value)

  saga_reply_topic_arns = {
    "execution-events" = nonsensitive(data.aws_ssm_parameter.execution_events_topic_arn.value)
    "billing-events"   = nonsensitive(data.aws_ssm_parameter.billing_events_topic_arn.value)
  }
}
