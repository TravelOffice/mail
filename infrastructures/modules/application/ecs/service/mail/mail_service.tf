variable "SERVICE_NAME" {}
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

resource "aws_security_group" "mail" {
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

resource "aws_ecs_service" "app" {
  name                   = var.SERVICE_NAME
  cluster                = var.ECS_CLUSTER_ID
  task_definition        = var.MAIL_TASK_ARN
  desired_count          = var.DESIRED_COUNT
  enable_execute_command = true

  load_balancer {
    target_group_arn = var.MAIL_TARGET_GROUP_ARN
    container_name   = var.CONTAINER_NAME
    container_port   = 8080
  }
  # For network awsvpc only
  network_configuration {
    subnets          = var.SUBNETS
    security_groups  = [aws_security_group.mail.id]
    assign_public_ip = true
  }
}
