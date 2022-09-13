variable "ENV" {}
variable "FEATURE_NAME" {}
variable "TAGS" {}

module "MAIL_QUEUE" {
  source     = "./mail"
  QUEUE_NAME = "${var.ENV}-${var.FEATURE_NAME}-mail_queue"
  TAGS       = var.TAGS
}

output "mail_queue_arn" {
  value = module.MAIL_QUEUE.mail_queue_arn
}

output "mail_queue_name" {
  value = module.MAIL_QUEUE.mail_queue_name
}

output "mail_queue_url" {
  value = module.MAIL_QUEUE.mail_queue_url
}

