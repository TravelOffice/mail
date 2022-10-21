variable "LB_NAME" {}
variable "VPC_ID" {}
variable "SUBNETS" {}
variable "SECURITY_GROUPS" {}
variable "DOMAIN_ARN" {}
variable "TAGS" {}

module "MAIL_ALB" {
  source  = "terraform-aws-modules/alb/aws"
  version = "8.1.0"

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
        healthy_threshold   = 2
        matcher             = "200"
        unhealthy_threshold = 3
        interval            = 30
        path                = "/healthcheck"
      }
      stickiness = {
        enabled = false
        type    = "lb_cookie"
      }
    },
    {
      name_prefix          = "Search"
      backend_protocol     = "HTTP"
      backend_port         = 80
      target_type          = "instance"
      deregistration_delay = 60
      health_check = {
        healthy_threshold   = 2
        matcher             = "200"
        unhealthy_threshold = 3
        interval            = 60
        path                = "/health"
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

  https_listener_rules = [
    {
      https_listener_index = 0
      actions = [
        {
          type               = "forward"
          target_group_index = 1
        }
      ]
      conditions = [
        {
          path_patterns = [
            "/search",
            "/search/*",
            "/static/*",
            "/manifest.json",
          ]
        }
      ]
    },
    {
      https_listener_index = 0
      actions = [
        {
          type               = "forward"
          target_group_index = 1
        }
      ]
      conditions = [
        {
          http_headers = [{
            http_header_name = "x-meilisearch-client"
            values           = ["*"]
          }]
        }
      ]
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

output "search_lb_target_group_arn" {
  value = module.MAIL_ALB.target_group_arns[1]
}
