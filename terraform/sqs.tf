resource "aws_sqs_queue" "saga_replies_dlq" {
  name                        = local.saga_replies_dlq_name
  fifo_queue                  = true
  content_based_deduplication = false
  message_retention_seconds   = local.dlq_retention_seconds

  tags = { Name = local.saga_replies_dlq_name }
}

resource "aws_sqs_queue" "saga_replies" {
  name                        = local.saga_replies_queue_name
  fifo_queue                  = true
  content_based_deduplication = false
  visibility_timeout_seconds  = local.visibility_timeout_seconds
  receive_wait_time_seconds   = local.receive_wait_time_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.saga_replies_dlq.arn
    maxReceiveCount     = local.max_receive_count
  })

  tags = { Name = local.saga_replies_queue_name }
}

data "aws_iam_policy_document" "saga_replies" {
  statement {
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.saga_replies.arn]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = values(local.saga_reply_topic_arns)
    }
  }
}

resource "aws_sqs_queue_policy" "saga_replies" {
  queue_url = aws_sqs_queue.saga_replies.id
  policy    = data.aws_iam_policy_document.saga_replies.json
}

resource "aws_sns_topic_subscription" "saga_replies" {
  for_each = local.saga_reply_topic_arns

  topic_arn            = each.value
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.saga_replies.arn
  raw_message_delivery = true
}
