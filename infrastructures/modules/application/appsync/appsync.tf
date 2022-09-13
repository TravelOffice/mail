variable "ENV" {}
variable "FEATURE_NAME" {}
variable "APPSYNC_API_ID" {}
variable "APPSYNC_RESOLVERS" {}
variable "MAIL_QUEUE_ARN" {}
variable "MAIL_QUEUE_URL" {}
variable "AWS_REGION" {}
variable "TAGS" {}

module "APPSYNC_DATA_SOURCE" {
  source         = "./data_source"
  ENV            = var.ENV
  FEATURE_NAME   = var.FEATURE_NAME
  APPSYNC_API_ID = var.APPSYNC_API_ID
  AWS_REGION     = var.AWS_REGION
  MAIL_QUEUE_ARN = var.MAIL_QUEUE_ARN
  MAIL_QUEUE_URL = var.MAIL_QUEUE_URL
  TAGS           = var.TAGS
}

module "APPSYNC_RESOLVER" {
  source            = "./resolver"
  ENV               = var.ENV
  FEATURE_NAME      = var.FEATURE_NAME
  APPSYNC_API_ID    = var.APPSYNC_API_ID
  APPSYNC_RESOLVERS = var.APPSYNC_RESOLVERS
  RESOLVER_INPUT_VARS = {
    MailQueueUrl = var.MAIL_QUEUE_URL
  }
  depends_on = [
    module.APPSYNC_DATA_SOURCE
  ]
}
