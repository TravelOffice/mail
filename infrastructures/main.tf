data "http" "infra" {
  url = "https://raw.githubusercontent.com/TravelOffice/infrastructure-constant/master/${var.ENV}-infrastructure.json"
}

variable "TF_BUCKET" {}
variable "APPSYNC_RESOLVERS" {}
variable "ENV" {}

locals {
  constants           = jsondecode(data.http.infra.response_body)
  remote_feature_name = "base_infrastructure"
  env_vars = {
    standard_vars = {
      ENV                                                    = var.ENV
      AWS_REGION                                             = var.AWS_REGION
      SECRET_MANAGER_ID                                      = module.REMOTE_STATE.STATE.MARKETING.backend.secret_manager.id
      "spring.security.oauth2.resourceserver.jwt.issuer-uri" = "https://cognito-idp.${var.AWS_REGION}.amazonaws.com/${module.REMOTE_STATE.STATE.COMMON_CONFIG.cognito.user_pool.id}"
      MAX_MESSAGE_BRIEF_CONTENT_SIZE                         = "200"
      CORS_ALLOW_ORIGINS                                     = "*"
      MAIL_GOOGLE_CLIENT_ID                                  = local.constants["MAIL_GOOGLE_CLIENT_ID"]
      MAIL_GOOGLE_CLIENT_SECRET                              = local.constants["MAIL_GOOGLE_CLIENT_SECRET"]
      MAIL_AZURE_CLIENT_ID                                   = local.constants["MAIL_AZURE_CLIENT_ID"]
      MAIL_AZURE_CLIENT_SECRET                               = local.constants["MAIL_AZURE_CLIENT_SECRET"]
      MAIL_OAUTH2_REDIRECT_ENDPOINT                          = format("%s%s%s", "https://sso.", module.REMOTE_STATE.STATE.COMMON_CONFIG.route53.hosted_zone.domain.name, "/mail/callback")
      MAIL_SEARCH_QUEUE_URL                                  = module.MAIL_QUEUE.search_queue_url
    }
  }

  tags = {
    Env       = var.ENV
    Terraform = true
    Feature   = var.FEATURE_NAME
  }

  MESSAGE_PER_TASK        = 5
  MEILI_SEARCH_MASTER_KEY = local.constants["MEILI_SEARCH_MASTER_KEY"]
}

module "REMOTE_STATE" {
  source              = "../../modules/tf_remote_state"
  ENV                 = var.ENV
  REMOTE_TF_BUCKET    = var.TF_BUCKET
  REMOTE_FEATURE_NAME = local.remote_feature_name
  REMOTE_AWS_REGION   = var.AWS_REGION
}

module "MAIL_LOAD_BALANCER" {
  source             = "../../modules/application/alb"
  VPC_ID             = module.REMOTE_STATE.STATE.MARKETING.backend.vpc.id
  LB_ARN             = module.REMOTE_STATE.STATE.MARKETING.search.lb_arn
  LB_FRONT_END_HTTPS = module.REMOTE_STATE.STATE.MARKETING.search.lb_front_end_https_arn
  LB_FRONT_END_HTTP  = module.REMOTE_STATE.STATE.MARKETING.search.lb_front_end_http_arn
  DOMAIN_ARN         = module.REMOTE_STATE.STATE.COMMON_CONFIG.route53.hosted_zone.domain.certificate.api_gateway
  TAGS               = local.tags
}

module "ECS" {
  source       = "../../modules/application/ecs"
  FEATURE_NAME = var.FEATURE_NAME
  ENV          = var.ENV
  CUSTOM_POLICIES = [
    "EcsTaskStandardRole",
    "DynamoTableReadWrite",
    "SQSLambdaTriggerRole",
    "SecretManagerRead",
    "SQSSendMessage"
  ]
  AWS_POLICIES = [
    "service-role/AmazonECSTaskExecutionRolePolicy"
  ]
  MAIL_API_TASK_ENV = merge(
    local.env_vars.standard_vars,
    {
      "spring.profiles.active" = "mail_api"
    }
  )
  MAIL_QUEUE_TASK_ENV = merge(
    local.env_vars.standard_vars,
    {
      MAIL_SERVICE_QUEUE_URL             = module.MAIL_QUEUE.mail_queue_url
      MAIL_MAX_MESSAGE_PER_SERVICE_TASK  = tostring(local.MESSAGE_PER_TASK)
      "spring.profiles.active"           = "mail_queue"
      "spring.main.web-application-type" = "none"
    }
  )
  TAGS                     = local.tags
  AWS_REGION               = var.AWS_REGION
  PUBLIC_SUBNETS           = module.REMOTE_STATE.STATE.MARKETING.backend.vpc.public_subnets
  PRIVATE_SUBNETS          = module.REMOTE_STATE.STATE.MARKETING.backend.vpc.private_subnets
  VPC_ID                   = module.REMOTE_STATE.STATE.MARKETING.backend.vpc.id
  MAIL_TARGET_GROUP_ARN    = module.MAIL_LOAD_BALANCER.mail_lb_target_group_arn
  SEARCH_TARGET_GROUP_ARN  = module.MAIL_LOAD_BALANCER.search_lb_target_group_arn
  MAIL_SERVICE_QUEUE_URL   = module.MAIL_QUEUE.mail_queue_name
  MESSAGE_PER_TASK         = local.MESSAGE_PER_TASK
  MEILI_SEARCH_MASTER_KEY  = local.MEILI_SEARCH_MASTER_KEY
  SEARCH_MESSAGE_QUEUE_URL = module.MAIL_QUEUE.search_queue_url
  SEARCH_MESSAGE_QUEUE_ARN = module.MAIL_QUEUE.search_queue_arn
  SCRIPT_BUCKET            = module.REMOTE_STATE.STATE.MARKETING.s3.script_bucket
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

// Add value to secret manager
module "SECRET_MANAGER" {
  source                   = "../../modules/application/secret"
  SECRET_MANAGER_ID        = module.REMOTE_STATE.STATE.MARKETING.backend.secret_manager.id
  SEARCH_ENGINE_MASTER_KEY = local.MEILI_SEARCH_MASTER_KEY
}
