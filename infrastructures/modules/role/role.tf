variable "CUSTOM_POLICIES" {}
variable "AWS_POLICIES" {}
variable "ASSUME_ROLE_POLICY" {}
variable "NAME" {}
variable "TAGS" {}

data "http" "custom_policy" {
  url = "https://raw.githubusercontent.com/TravelOffice/terraform-policy-database/master/custom_policy.json"
}
locals {
  custom_policies = flatten([
    for policy in var.CUSTOM_POLICIES : [
      jsondecode(data.http.custom_policy.response_body)[policy]
    ]
  ])
  aws_policies = flatten([
    for policy in var.AWS_POLICIES : [
      format("%s/%s", "arn:aws:iam::aws:policy", policy)
    ]
  ])
}

resource "aws_iam_policy" "policy" {
  name_prefix = lower("${var.NAME}")

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = "${local.custom_policies}"
  })

  tags = var.TAGS
}

resource "aws_iam_role" "role" {
  name_prefix         = var.NAME
  assume_role_policy  = var.ASSUME_ROLE_POLICY
  managed_policy_arns = concat([aws_iam_policy.policy.arn], local.aws_policies)
  tags                = var.TAGS
}

output "arn" {
  value = aws_iam_role.role.arn
}