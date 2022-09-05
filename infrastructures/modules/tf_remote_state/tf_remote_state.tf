variable "ENV" {}
variable "REMOTE_TF_BUCKET" {}
variable "REMOTE_FEATURE_NAME" {}
variable "REMOTE_AWS_REGION" {}

data "terraform_remote_state" "remote_state" {
  backend = "s3"
  config = {
    bucket = var.REMOTE_TF_BUCKET
    key    = "${var.REMOTE_FEATURE_NAME}/environments/${var.ENV}/terraform.tfstate"
    region = var.REMOTE_AWS_REGION
  }
}

output "STATE" {
  value = data.terraform_remote_state.remote_state.outputs
}
