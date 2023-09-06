variable "DOMAIN_ARN" {}
variable "TAGS" {}
variable "LB_ARN" {}
variable "LB_FRONT_END_HTTPS" {}
variable "LB_FRONT_END_HTTP" {}
variable "VPC_ID" {}

variable "target_groups" {
  type = any
  default = [
    {
      // Mail service
      name_prefix      = "MailSv"
      backend_protocol = "HTTP"
      backend_port     = 80
      target_type      = "ip"
      health_check = {
        healthy_threshold   = 2
        matcher             = "200"
        unhealthy_threshold = 3
        interval            = 30
        path                = "/mail/healthcheck"
      }
      stickiness = {
        enabled = false
        type    = "lb_cookie"
      }
    },
    {
      // Mail search
      name_prefix          = "MailSr"
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
}

variable "https_listener_rules" {
  type = any
  default = [
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
            "/mail/search",
            "/mail/search/*"
          ]
        }
      ]
      # For mail priority can comes from 1 to 09
      priority = 01
    },
    {
      https_listener_index = 0
      actions = [
        {
          type               = "forward"
          target_group_index = 0
        }
      ]
      conditions = [
        {
          path_patterns = [
            "/mail",
            "/mail/*"
          ]
        }
      ]
      # For mail priority can comes from 1 to 09
      priority = 02
    }
  ]
}

module "MAIL_LB" {
  source               = "TravelOffice/application-loadbalancer/aws"
  vpc_id               = var.VPC_ID
  https_listener_rules = var.https_listener_rules
  lb_front_end_http    = var.LB_FRONT_END_HTTP
  lb_front_end_https   = var.LB_FRONT_END_HTTPS
  target_groups        = var.target_groups
  tags                 = var.TAGS
}

output "mail_lb_target_group_arn" {
  value = module.MAIL_LB.search_lb_target_group_arn[0]
}

output "search_lb_target_group_arn" {
  value = module.MAIL_LB.search_lb_target_group_arn[1]
}

