output "target_group_arn" {
  description = "Target group dos pods, ligado a eles pelo TargetGroupBinding."
  value       = aws_lb_target_group.service.arn
}

output "listener_rule_path" {
  description = "Caminho que a regra do ALB interno encaminha para este serviço."
  value       = "${local.path_prefix}/*"
}

output "db_identifier" {
  description = "Identificador da instância RDS."
  value       = aws_db_instance.main.identifier
}

output "db_endpoint" {
  description = "Hostname da instância, sem a porta."
  value       = aws_db_instance.main.address
}

output "password_ssm_parameter" {
  description = "Onde buscar a senha: aws ssm get-parameter --name <isto> --with-decryption"
  value       = aws_ssm_parameter.rds_password.name
}

output "saga_replies_queue_url" {
  description = "Fila de respostas do orquestrador, assinada aos tópicos de eventos dos participantes."
  value       = aws_sqs_queue.saga_replies.url
}

output "saga_replies_dlq_url" {
  description = "DLQ da fila de respostas; mensagem aqui significa resposta que o orquestrador não conseguiu processar."
  value       = aws_sqs_queue.saga_replies_dlq.url
}

output "saga_reply_topics" {
  description = "Tópicos cujos eventos chegam à fila de respostas."
  value       = keys(local.saga_reply_topic_arns)
}
