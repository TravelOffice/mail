variable "AWS_REGION" {
  default = "ap-southeast-1"
}

variable "BRANCH_NAME" {
  default = "dev"
}

variable "FEATURE_NAME" {
  default = "mail"
}

variable "SCM_CONSTANT_ARN" {
  default = "arn:aws:secretsmanager:ap-southeast-1:087112488386:secret:dev-infrastructure-constant-91TPFe"
}
