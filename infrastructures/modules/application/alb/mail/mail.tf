variable "LB_NAME" {}
variable "VPC_ID" {}
variable "SUBNETS" {}
variable "SECURITY_GROUPS" {}
variable "DOMAIN_ARN" {}
variable "TAGS" {}

module "MAIL_ALB" {
  source  = "terraform-aws-modules/alb/aws"
  version = "~> 6.0"

  name = var.LB_NAME

  load_balancer_type = "application"

  vpc_id          = var.VPC_ID
  subnets         = var.SUBNETS
  security_groups = var.SECURITY_GROUPS

  target_groups = [
    {
      name_prefix      = "MailTG"
      backend_protocol = "HTTP"
      backend_port     = 80
      target_type      = "ip"
      health_check = {
        healthy_threshold   = 3
        matcher             = "200"
        unhealthy_threshold = 2
        interval            = 60
        path                = "/healthcheck"
      }
      stickiness = {
        enabled = false
        type    = "lb_cookie"
      }
    }
  ]

  https_listeners = [
    {
      port               = 443
      protocol           = "HTTPS"
      certificate_arn    = var.DOMAIN_ARN
      target_group_index = 0
    }
  ]

  http_tcp_listeners = [
    {
      port        = 80
      protocol    = "HTTP"
      action_type = "redirect"
      redirect = {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  ]

  tags = var.TAGS
}

output "mail_lb_arn" {
  value = module.MAIL_ALB.lb_arn
}


output "mail_lb_zone_id" {
  value = module.MAIL_ALB.lb_zone_id
}

output "mail_lb_dns" {
  value = module.MAIL_ALB.lb_dns_name
}

output "mail_lb_target_group_arn" {
  value = module.MAIL_ALB.target_group_arns[0]
}
