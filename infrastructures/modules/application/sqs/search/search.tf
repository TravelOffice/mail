variable "QUEUE_NAME" {}
variable "TAGS" {}

resource "aws_sqs_queue" "search_queue_arn" {
  # Queue name
  name_prefix = var.QUEUE_NAME
  # The time for which a ReceiveMessage call will wait for a message to arrive (long polling) before returning.
  # An integer from 0 to 20 (seconds).
  receive_wait_time_seconds = 5

  # The visibility timeout for the queue. An integer from 0 to 43200 (12 hours). The default for this attribute is 30
  visibility_timeout_seconds = 180

  # Policy as object
  policy = jsonencode({})

  tags = var.TAGS
}

output "search_queue_arn" {
  value = aws_sqs_queue.search_queue_arn.arn
}

output "search_queue_name" {
  value = aws_sqs_queue.search_queue_arn.name
}

output "search_queue_url" {
  value = aws_sqs_queue.search_queue_arn.url
}
