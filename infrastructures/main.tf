variable "TF_BUCKET" {}
variable "APPSYNC_RESOLVERS" {}
variable "ENV" {}

locals {
  remote_feature_name = "base_infrastructure"
  env_vars = {
    standard_vars = {
      ENV                                                    = var.ENV
      AWS_REGION                                             = var.AWS_REGION
      SECRET_MANAGER_ID                                      = module.REMOTE_STATE.STATE.MARKETING.backend.secret_manager.id
      "spring.security.oauth2.resourceserver.jwt.issuer-uri" = "https://cognito-idp.${var.AWS_REGION}.amazonaws.com/${module.REMOTE_STATE.STATE.COMMON_CONFIG.cognito.user_pool.id}"
      MAX_MESSAGE_BRIEF_CONTENT_SIZE                         = "200"
    }
  }

  tags = {
    Env       = var.ENV
    Terraform = true
    Feature   = var.FEATURE_NAME
  }

  MESSAGE_PER_TASK = 5
}

module "REMOTE_STATE" {
  source              = "../../modules/tf_remote_state"
  ENV                 = var.ENV
  REMOTE_TF_BUCKET    = var.TF_BUCKET
  REMOTE_FEATURE_NAME = local.remote_feature_name
  REMOTE_AWS_REGION   = var.AWS_REGION
}

module "MAIL_LOAD_BALANCER" {
  source       = "../../modules/application/alb"
  ENV          = var.ENV
  FEATURE_NAME = var.FEATURE_NAME
  SUBNETS      = module.REMOTE_STATE.STATE.BASE_INFRASTRUCTURE.backend.vpc.public_subnets
  VPC_ID       = module.REMOTE_STATE.STATE.BASE_INFRASTRUCTURE.backend.vpc.id
  DOMAIN_ARN   = module.REMOTE_STATE.STATE.COMMON_CONFIG.route53.hosted_zone.domain.certificate.arn
  TAGS         = local.tags
}

module "ECS" {
  source       = "../../modules/application/ecs"
  FEATURE_NAME = var.FEATURE_NAME
  ENV          = var.ENV
  CUSTOM_POLICIES = [
    "EcsTaskStandardRole",
    "DynamoTableReadWrite",
    "SQSLambdaTriggerRole",
    "SecretManagerRead"
  ]
  AWS_POLICIES = [
    "service-role/AmazonECSTaskExecutionRolePolicy"
  ]
  MAIL_API_TASK_ENV = local.env_vars.standard_vars
  MAIL_QUEUE_TASK_ENV = merge(
    local.env_vars.standard_vars,
    {
      MAIL_SERVICE_QUEUE_NAME           = module.MAIL_QUEUE.mail_queue_url
      MAIL_MAX_MESSAGE_PER_SERVICE_TASK = tostring(local.MESSAGE_PER_TASK)
    }
  )
  TAGS                    = local.tags
  AWS_REGION              = var.AWS_REGION
  SUBNETS                 = module.REMOTE_STATE.STATE.BASE_INFRASTRUCTURE.backend.vpc.public_subnets
  VPC_ID                  = module.REMOTE_STATE.STATE.BASE_INFRASTRUCTURE.backend.vpc.id
  MAIL_TARGET_GROUP_ARN   = module.MAIL_LOAD_BALANCER.mail_lb_target_group_arn
  MAIL_SERVICE_QUEUE_NAME = module.MAIL_QUEUE.mail_queue_name
  MESSAGE_PER_TASK        = local.MESSAGE_PER_TASK
}

resource "aws_route53_record" "mail_domain" {
  zone_id = module.REMOTE_STATE.STATE.COMMON_CONFIG.route53.hosted_zone.id
  name    = lower("${var.ENV}-${var.FEATURE_NAME}")
  type    = "A"
  alias {
    name                   = module.MAIL_LOAD_BALANCER.mail_lb_dns
    zone_id                = module.MAIL_LOAD_BALANCER.mail_lb_zone_id
    evaluate_target_health = true
  }
}

module "MAIL_QUEUE" {
  source       = "../../modules/application/sqs"
  FEATURE_NAME = var.FEATURE_NAME
  ENV          = var.ENV
  TAGS         = local.tags
}

module "APPSYNC_CONFIGURATION" {
  source            = "../../modules/application/appsync"
  ENV               = var.ENV
  FEATURE_NAME      = var.FEATURE_NAME
  APPSYNC_API_ID    = module.REMOTE_STATE.STATE.MARKETING.backend.appsync.id
  APPSYNC_RESOLVERS = var.APPSYNC_RESOLVERS
  AWS_REGION        = var.AWS_REGION
  MAIL_QUEUE_ARN    = module.MAIL_QUEUE.mail_queue_arn
  MAIL_QUEUE_URL    = module.MAIL_QUEUE.mail_queue_url
  TAGS              = local.tags
}
