variable "AWS_REGION" {
  default = "ap-southeast-1"
}

variable "BRANCH_NAME" {
  default = "master"
}

variable "FEATURE_NAME" {
  default = "mail"
}

variable "SCM_CONSTANT_ARN" {
  default = "arn:aws:secretsmanager:ap-southeast-1:800823848420:secret:prod-infrastructure-constant-FEt8TM"
}
