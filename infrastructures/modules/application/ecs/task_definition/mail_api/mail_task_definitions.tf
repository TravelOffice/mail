variable "TASK_ROLE_ARN" {}
variable "TASK_NAME" {}
variable "IMAGE_URI" {}
variable "TASK_CPU" {}
variable "TASK_RAM" {}
variable "ENV" {}
variable "MAIL_TASK_ENV" {}
variable "AWS_REGION" {}
variable "TAGS" {}


resource "aws_cloudwatch_log_group" "task_log" {
  name_prefix       = "mail/${var.TASK_NAME}/api"
  retention_in_days = var.ENV == "dev" ? 1 : 7

  tags = var.TAGS
}

data "aws_arn" "task_log" {
  arn = aws_cloudwatch_log_group.task_log.arn
}

resource "aws_ecs_task_definition" "task" {
  family                   = var.TASK_NAME
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  task_role_arn            = var.TASK_ROLE_ARN
  execution_role_arn       = var.TASK_ROLE_ARN
  cpu                      = var.TASK_CPU
  memory                   = var.TASK_RAM
  container_definitions = jsonencode([
    {
      name                   = var.TASK_NAME
      image                  = var.IMAGE_URI
      essential              = true
      readonlyRootFilesystem = false
      linuxParameters = {
        initProcessEnabled = true
      }
      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
        }
      ]
      environment = [for k, v in var.MAIL_TASK_ENV : {
        name  = k
        value = v
      }]
      logConfiguration = {
        logDriver     = "awslogs",
        secretOptions = null,
        options = {
          awslogs-group         = substr(data.aws_arn.task_log.resource, length("log-group:"), -1),
          awslogs-region        = "${var.AWS_REGION}",
          awslogs-stream-prefix = "ecs"
        }
      }
    }
  ])

}

output "task_arn" {
  value = aws_ecs_task_definition.task.arn
}
