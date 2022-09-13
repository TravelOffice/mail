variable "ENV" {}
variable "FEATURE_NAME" {}
variable "APPSYNC_API_ID" {}
variable "APPSYNC_FUNCTIONS" {}
variable "TAGS" {}

locals {
  root_path = format("%s/../../..", path.root)
}

resource "aws_appsync_function" "functions" {
  for_each                  = var.APPSYNC_FUNCTIONS
  api_id                    = var.APPSYNC_API_ID
  data_source               = lower("${var.ENV}_${each.value.data_source}")
  name                      = lower("${var.ENV}_${each.key}")
  request_mapping_template  = file(format("%s/%s", local.root_path, each.value.request_template.path))
  response_mapping_template = file(format("%s/%s", local.root_path, each.value.response_template.path))
}

output "appsync_function_id" {
  value = { for key, value in var.APPSYNC_FUNCTIONS : key => aws_appsync_function.functions[key].function_id }
}
