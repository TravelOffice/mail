variable "VPC_ID" {}
variable "SUBNETS" {}
variable "DOMAIN_ARN" {}
variable "TAGS" {}
variable "ENV" {}
variable "FEATURE_NAME" {}

resource "aws_security_group" "mail_lb" {
  name_prefix = "mail-lb"
  description = "Allow http/https to the load balancer"
  vpc_id      = var.VPC_ID

  ingress {
    description      = "Allow HTTPS to LB"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description      = "Allow HTTP to LB"
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
  tags = var.TAGS
}

module "MAIL_LB" {
  source          = "./mail"
  LB_NAME         = lower("${var.ENV}-${var.FEATURE_NAME}")
  VPC_ID          = var.VPC_ID
  SUBNETS         = var.SUBNETS
  SECURITY_GROUPS = [aws_security_group.mail_lb.id]
  DOMAIN_ARN      = var.DOMAIN_ARN
  TAGS            = var.TAGS
}

output "mail_lb_arn" {
  value = module.MAIL_LB.mail_lb_arn
}

output "mail_lb_zone_id" {
  value = module.MAIL_LB.mail_lb_zone_id
}

output "mail_lb_dns" {
  value = module.MAIL_LB.mail_lb_dns
}

output "mail_lb_target_group_arn" {
  value = module.MAIL_LB.mail_lb_target_group_arn
}

