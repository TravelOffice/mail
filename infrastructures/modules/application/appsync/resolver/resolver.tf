variable "ENV" {}
variable "FEATURE_NAME" {}
variable "APPSYNC_API_ID" {}
variable "APPSYNC_RESOLVERS" {}
variable "RESOLVER_INPUT_VARS" {

}

locals {
  appsync_default_config = {
    kind              = "UNIT",
    request_template  = "{}"
    response_template = "{}"
    data_source       = null
  }
  root_path = format("%s/../../..", path.root)
}

# UNIT type resolver (default)
resource "aws_appsync_resolver" "resolver" {
  for_each          = var.APPSYNC_RESOLVERS
  api_id            = var.APPSYNC_API_ID
  field             = each.value.field
  type              = each.value.type
  kind              = try(each.value.kind, local.appsync_default_config.kind)
  data_source       = can(each.value.data_source) ? lower("${var.ENV}_${each.value.data_source}") : local.appsync_default_config.data_source
  request_template  = can(each.value.request_template) ? templatefile(format("%s/%s", local.root_path, each.value.request_template.path), var.RESOLVER_INPUT_VARS) : local.appsync_default_config.request_template
  response_template = can(each.value.response_template) ? file(format("%s/%s", local.root_path, each.value.response_template.path)) : local.appsync_default_config.response_template

  dynamic "caching_config" {
    for_each = can(each.value.caching_config) ? [each.value.caching_config] : []
    content {
      caching_keys = caching_config.value.caching_keys
      ttl          = caching_config.value.ttl
    }
  }
}
