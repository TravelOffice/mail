variable "SECRET_MANAGER_ID" {}
variable "SEARCH_ENGINE_ENDPOINT" {}
variable "SEARCH_ENGINE_MASTER_KEY" {}

locals {
  root_path = format("%s/../../..", path.root)
}

resource "null_resource" "appsync_lambda_authorizer" {
  triggers = {
    always_run = "${timestamp()}"
  }
  provisioner "local-exec" {
    command = format(
      "%s/%s %s https://%s %s",
      local.root_path,
      "scripts/update_secret.sh",
      var.SECRET_MANAGER_ID,
      var.SEARCH_ENGINE_ENDPOINT,
      var.SEARCH_ENGINE_MASTER_KEY
    )
  }
}
