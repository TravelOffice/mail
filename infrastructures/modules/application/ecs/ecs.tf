variable "ENV" {}
variable "FEATURE_NAME" {}
variable "CUSTOM_POLICIES" {
  default = []
}
variable "AWS_POLICIES" {
  default = []
}
variable "MAIL_QUEUE_TASK_ENV" {}
variable "MAIL_API_TASK_ENV" {}
variable "AWS_REGION" {}
variable "TAGS" {}
variable "MAIL_TARGET_GROUP_ARN" {}
variable "SUBNETS" {}
variable "VPC_ID" {}
variable "MAIL_SERVICE_QUEUE_NAME" {}

locals {
  cluster_name = "${upper("${var.ENV}-${var.FEATURE_NAME}")}-CLUSTER"
}

module "ECS_CLUSTER" {
  source  = "terraform-aws-modules/ecs/aws"
  version = "4.1.1"

  cluster_name = local.cluster_name

  fargate_capacity_providers = {
    FARGATE = {
      default_capacity_provider_strategy = {
        weight = 100
      }
    }
  }

  tags = var.TAGS
}

resource "aws_ecr_repository" "repo" {
  name = lower("${var.ENV}-${var.FEATURE_NAME}")
}

module "ECS_MAIL_DOCKER_IMAGE" {
  source  = "terraform-aws-modules/lambda/aws//modules/docker-build"
  version = "2.34.0"

  ecr_repo      = split("/", aws_ecr_repository.repo.repository_url)[1]
  ecr_repo_tags = var.TAGS
  source_path   = format("%s/%s", "../../..", "src/modules/mail_service")

  ecr_repo_lifecycle_policy = <<EOF
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep only the last 2 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 2
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
EOF
}

data "aws_iam_policy_document" "assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

module "ROLE" {
  source             = "../../role"
  NAME               = "${lower("${var.ENV}-${var.FEATURE_NAME}")}-TaskRole"
  CUSTOM_POLICIES    = var.CUSTOM_POLICIES
  AWS_POLICIES       = var.AWS_POLICIES
  ASSUME_ROLE_POLICY = data.aws_iam_policy_document.assume_role_policy.json
  TAGS               = var.TAGS
}

module "ECS_MAIL_API_TASK_DEFINITION" {
  source        = "./task_definition/mail"
  TASK_NAME     = lower("${var.ENV}-${var.FEATURE_NAME}_api")
  TASK_ROLE_ARN = module.ROLE.arn
  IMAGE_URI     = module.ECS_MAIL_DOCKER_IMAGE.image_uri
  TASK_CPU      = 256
  TASK_RAM      = 512
  MAIL_TASK_ENV = var.MAIL_API_TASK_ENV
  AWS_REGION    = var.AWS_REGION
  ENV           = var.ENV
  TAGS          = var.TAGS
}

module "ECS_MAIL_API_SERVICE" {
  source                = "./service/mail_api_service"
  ENV                   = var.ENV
  FEATURE_NAME          = var.FEATURE_NAME
  SERVICE_NAME          = "${var.ENV}-${var.FEATURE_NAME}-service"
  ECS_CLUSTER_ID        = module.ECS_CLUSTER.cluster_id
  ECS_CLUSTER_NAME      = module.ECS_CLUSTER.cluster_name
  MAIL_TASK_ARN         = module.ECS_MAIL_API_TASK_DEFINITION.task_arn
  DESIRED_COUNT         = 1
  MIN_CAPACITY          = 1
  MAX_CAPACITY          = 5
  MAIL_TARGET_GROUP_ARN = var.MAIL_TARGET_GROUP_ARN
  CONTAINER_NAME        = lower("${var.ENV}-${var.FEATURE_NAME}")
  SUBNETS               = var.SUBNETS
  VPC_ID                = var.VPC_ID
  TAGS                  = var.TAGS
}

module "ECS_MAIL_QUEUE_TASK_DEFINITION" {
  source        = "./task_definition/mail"
  TASK_NAME     = lower("${var.ENV}-${var.FEATURE_NAME}_queue")
  TASK_ROLE_ARN = module.ROLE.arn
  IMAGE_URI     = module.ECS_MAIL_DOCKER_IMAGE.image_uri
  TASK_CPU      = 256
  TASK_RAM      = 512
  MAIL_TASK_ENV = var.MAIL_QUEUE_TASK_ENV
  AWS_REGION    = var.AWS_REGION
  ENV           = var.ENV
  TAGS          = var.TAGS
}

module "ECS_MAIL_QUEUE_SERVICE" {
  source                  = "./service/mail_queue_service"
  ENV                     = var.ENV
  FEATURE_NAME            = var.FEATURE_NAME
  SERVICE_NAME            = "${var.ENV}-${var.FEATURE_NAME}-service"
  ECS_CLUSTER_ID          = module.ECS_CLUSTER.cluster_id
  ECS_CLUSTER_NAME        = module.ECS_CLUSTER.cluster_name
  MAIL_TASK_ARN           = module.ECS_MAIL_QUEUE_TASK_DEFINITION.task_arn
  MAIL_SERVICE_QUEUE_NAME = var.MAIL_SERVICE_QUEUE_NAME
  DESIRED_COUNT           = 0
  MIN_CAPACITY            = 0
  MAX_CAPACITY            = 5
  CONTAINER_NAME          = lower("${var.ENV}-${var.FEATURE_NAME}")
  SUBNETS                 = var.SUBNETS
  VPC_ID                  = var.VPC_ID
  TAGS                    = var.TAGS
}


output "name" {
  value = module.ECS_CLUSTER.cluster_name
}
