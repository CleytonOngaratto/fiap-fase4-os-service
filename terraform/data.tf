data "aws_ssm_parameter" "vpc_id" {
  name = "/fase4/vpc/id"
}

data "aws_ssm_parameter" "private_subnets" {
  name = "/fase4/vpc/private-subnets"
}

data "aws_ssm_parameter" "eks_node_sg_id" {
  name = "/fase4/eks/node-sg-id"
}

data "aws_ssm_parameter" "alb_listener_arn" {
  name = "/fase4/alb/listener-arn"
}

data "aws_ssm_parameter" "execution_events_topic_arn" {
  name = "/fase4/sns/execution-events/topic-arn"
}

data "aws_ssm_parameter" "billing_events_topic_arn" {
  name = "/fase4/sns/billing-events/topic-arn"
}
