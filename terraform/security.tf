resource "aws_security_group" "rds" {
  name        = "${var.project}-os-rds"
  description = "RDS PostgreSQL do OS Service: aceita 5432 so dos pods do EKS."
  vpc_id      = local.vpc_id

  tags = { Name = "${var.project}-os-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_eks_nodes" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = local.eks_node_sg_id
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  description                  = "Pods do EKS (SG primario do cluster)"
}
