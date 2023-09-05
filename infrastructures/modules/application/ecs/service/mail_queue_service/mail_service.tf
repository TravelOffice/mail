variable "MIN_CAPACITY" {}
variable "MAX_CAPACITY" {}
variable "SERVICE_NAME" {}
variable "ECS_CLUSTER_NAME" {}
variable "ECS_CLUSTER_ID" {}
variable "MAIL_TASK_ARN" {}
variable "DESIRED_COUNT" {}
variable "MAIL_SERVICE_QUEUE_URL" {}
variable "MESSAGE_PER_TASK" {}
variable "CONTAINER_NAME" {}
variable "SUBNETS" {}
variable "VPC_ID" {}
variable "ENV" {}
variable "FEATURE_NAME" {}
variable "TAGS" {}

resource "aws_security_group" "mail_queue" {
  name_prefix = "ecs-mail-sg"
  description = "Allow all outbound traffic"
  vpc_id      = var.VPC_ID
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
  tags = var.TAGS
}

resource "aws_ecs_service" "mail_queue" {
  name            = "${var.SERVICE_NAME}_queue"
  cluster         = var.ECS_CLUSTER_ID
  task_definition = var.MAIL_TASK_ARN
  desired_count   = var.DESIRED_COUNT
  network_configuration {
    subnets          = var.SUBNETS
    security_groups  = [aws_security_group.mail_queue.id]
    assign_public_ip = false
  }

  capacity_provider_strategy {
    base              = 0
    capacity_provider = "FARGATE"
    weight            = 100
  }

  tags = var.TAGS
}

module "MAIL_SQS_AUTO_SCALE" {
  source                 = "./sqs_auto_scale"
  SERVICE_NAME           = aws_ecs_service.mail_queue.name
  ECS_CLUSTER_NAME       = var.ECS_CLUSTER_NAME
  MIN_CAPACITY           = var.MIN_CAPACITY
  MAX_CAPACITY           = var.MAX_CAPACITY
  MAIL_SERVICE_QUEUE_URL = var.MAIL_SERVICE_QUEUE_URL
  MESSAGE_PER_TASK       = var.MESSAGE_PER_TASK
  TAGS                   = var.TAGS
}

