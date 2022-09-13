variable "ENV" {}
variable "FEATURE_NAME" {}
variable "APPSYNC_API_ID" {}
variable "MAIL_QUEUE_ARN" {}
variable "MAIL_QUEUE_URL" {}
variable "AWS_REGION" {}
variable "TAGS" {}

resource "aws_iam_role" "iam_role" {
  name_prefix = "${var.ENV}-${var.FEATURE_NAME}-appsync-mail"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : "sts:AssumeRole",
        "Principal" : {
          "Service" : "appsync.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "role_policy" {
  name_prefix = "${var.ENV}-${var.FEATURE_NAME}-appsync-mail"
  role        = aws_iam_role.iam_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : "sqs:SendMessage",
        "Resource" : "${var.MAIL_QUEUE_ARN}"
      }
    ]
  })
}

resource "aws_appsync_datasource" "mail_queue_datasource" {
  api_id           = var.APPSYNC_API_ID
  name             = "${var.ENV}_mail_queue_datasource"
  type             = "HTTP"
  service_role_arn = aws_iam_role.iam_role.arn

  http_config {
    endpoint = "https://sqs.${var.AWS_REGION}.amazonaws.com"
    authorization_config {
      authorization_type = "AWS_IAM"
      aws_iam_config {
        signing_region       = var.AWS_REGION
        signing_service_name = "sqs"
      }
    }
  }
}

output "name" {
  value = "${var.ENV}_mail_queue_datasource"
}
