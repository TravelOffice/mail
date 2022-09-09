variable "MIN_CAPACITY" {}
variable "MAX_CAPACITY" {}
variable "SERVICE_NAME" {}
variable "ECS_CLUSTER_NAME" {}
variable "MAIL_SERVICE_QUEUE_NAME" {}
variable "TAGS" {}

resource "aws_appautoscaling_target" "target" {
  max_capacity       = var.MAX_CAPACITY
  min_capacity       = var.MIN_CAPACITY
  resource_id        = "service/${var.ECS_CLUSTER_NAME}/${var.SERVICE_NAME}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "initial-by-queue-metric" {
  depends_on         = [aws_appautoscaling_target.target]
  name               = "Initial By Queue Metric"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.target.resource_id
  scalable_dimension = aws_appautoscaling_target.target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.target.service_namespace

  step_scaling_policy_configuration {
    cooldown                = 60
    adjustment_type         = "ChangeInCapacity"
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_upper_bound = 1
      scaling_adjustment          = -1
    }
    step_adjustment {
      metric_interval_lower_bound = 1
      metric_interval_upper_bound = 5
      scaling_adjustment          = 1
    }
    step_adjustment {
      metric_interval_lower_bound = 5
      metric_interval_upper_bound = 10
      scaling_adjustment          = 2
    }
    step_adjustment {
      metric_interval_lower_bound = 10
      scaling_adjustment          = 3
    }
  }
}

resource "aws_appautoscaling_policy" "scaling-high-by-queue-metric" {
  depends_on         = [aws_appautoscaling_target.target]
  name               = "Scaling High By Queue Metric"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.target.resource_id
  scalable_dimension = aws_appautoscaling_target.target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.target.service_namespace

  step_scaling_policy_configuration {
    cooldown                = 60
    adjustment_type         = "PercentChangeInCapacity"
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_lower_bound = 10
      metric_interval_upper_bound = 15
      scaling_adjustment          = -25
    }

    step_adjustment {
      metric_interval_lower_bound = 15
      scaling_adjustment          = 25
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "queue-length" {
  alarm_name          = "ApproximateNumberOfMessagesVisible"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "1"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = "10"
  statistic           = "Average"
  threshold           = "0"

  dimensions = {
    QueueName = var.MAIL_SERVICE_QUEUE_NAME
  }

  alarm_description = "SQS queue [${var.MAIL_SERVICE_QUEUE_NAME}] length"

  alarm_actions = [
    aws_appautoscaling_policy.initial-by-queue-metric.arn,
    aws_appautoscaling_policy.scaling-high-by-queue-metric.arn
  ]
}
