variable "MIN_CAPACITY" {}
variable "MAX_CAPACITY" {}
variable "SERVICE_NAME" {}
variable "ECS_CLUSTER_NAME" {}
variable "ECS_CLUSTER_ID" {}
variable "MAIL_TASK_ARN" {}
variable "DESIRED_COUNT" {}
variable "MAIL_TARGET_GROUP_ARN" {}
variable "CONTAINER_NAME" {}
variable "SUBNETS" {}
variable "VPC_ID" {}
variable "ENV" {}
variable "FEATURE_NAME" {}
variable "TAGS" {}

resource "aws_security_group" "mail_api" {
  name_prefix = "ecs-mail-sg"
  description = "Allow all outbound traffic"
  vpc_id      = var.VPC_ID

  ingress {
    description      = "LB to ECS Task"
    from_port        = 8080
    to_port          = 8080
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

resource "aws_ecs_service" "mail_api" {
  name            = "${var.SERVICE_NAME}_api"
  cluster         = var.ECS_CLUSTER_ID
  task_definition = var.MAIL_TASK_ARN
  desired_count   = var.DESIRED_COUNT

  load_balancer {
    target_group_arn = var.MAIL_TARGET_GROUP_ARN
    container_name   = lower("${var.ENV}-${var.FEATURE_NAME}_api")
    container_port   = 8080
  }
  # For network awsvpc only
  network_configuration {
    subnets          = var.SUBNETS
    security_groups  = [aws_security_group.mail_api.id]
    assign_public_ip = true
  }

  capacity_provider_strategy {
    base              = 0
    capacity_provider = "FARGATE"
    weight            = 100
  }

  tags = var.TAGS
}

module "MAIL_API_AUTO_SCALE" {
  source           = "./api_auto_scale"
  SERVICE_NAME     = aws_ecs_service.mail_api.name
  ECS_CLUSTER_NAME = var.ECS_CLUSTER_NAME
  MIN_CAPACITY     = var.MIN_CAPACITY
  MAX_CAPACITY     = var.MAX_CAPACITY
  TAGS             = var.TAGS
}
