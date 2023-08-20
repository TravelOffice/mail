variable "DOMAIN_ARN" {}
variable "TAGS" {}
variable "LB_ARN" {}
variable "VPC_ID" {}

module "MAIL_LB" {
  source     = "./mail"
  VPC_ID     = var.VPC_ID
  LB_ARN     = var.LB_ARN
  DOMAIN_ARN = var.DOMAIN_ARN
  TAGS       = var.TAGS
}

output "mail_lb_target_group_arn" {
  value = module.MAIL_LB.mail_lb_target_group_arn
}

output "search_lb_target_group_arn" {
  value = module.MAIL_LB.search_lb_target_group_arn
}

