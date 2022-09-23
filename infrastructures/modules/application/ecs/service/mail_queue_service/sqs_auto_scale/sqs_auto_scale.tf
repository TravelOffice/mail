variable "MIN_CAPACITY" {}
variable "MAX_CAPACITY" {}
variable "SERVICE_NAME" {}
variable "ECS_CLUSTER_NAME" {}
variable "MAIL_SERVICE_QUEUE_NAME" {}
variable "MESSAGE_PER_TASK" {}
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
      metric_interval_lower_bound = 0
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
      metric_interval_lower_bound = 15
      scaling_adjustment          = 25
    }
  }
}

resource "aws_appautoscaling_policy" "scaling-down-by-queue-metric" {
  depends_on         = [aws_appautoscaling_target.target]
  name               = "Scaling Down By Queue Metric"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.target.resource_id
  scalable_dimension = aws_appautoscaling_target.target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.target.service_namespace

  step_scaling_policy_configuration {
    cooldown                = 60
    adjustment_type         = "ChangeInCapacity"
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_upper_bound = 0
      scaling_adjustment          = -1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "service_queue_low" {
  alarm_name          = "ApproximateNumberOfMessages"
  alarm_description   = "Total Visible+NonVisible"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = "1"
  threshold           = "0"
  alarm_actions = [
    aws_appautoscaling_policy.scaling-down-by-queue-metric.arn
  ]

  metric_query {
    id          = "sum"
    expression  = "visible+notvisible"
    label       = "Sum(Visible + NonVisible)"
    return_data = "true"
  }

  metric_query {
    id = "visible"

    metric {
      metric_name = "ApproximateNumberOfMessagesVisible"
      namespace   = "AWS/SQS"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        QueueName = var.MAIL_SERVICE_QUEUE_NAME
      }
    }
  }

  metric_query {
    id = "notvisible"

    metric {
      metric_name = "ApproximateNumberOfMessagesNotVisible"
      namespace   = "AWS/SQS"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        QueueName = var.MAIL_SERVICE_QUEUE_NAME
      }
    }
  }
  tags = var.TAGS
}

resource "aws_cloudwatch_metric_alarm" "service_queue_pending_message" {
  alarm_name          = "ApproximateNumberOfMessagesPending"
  alarm_description   = "Pending Message"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  threshold           = "0"
  alarm_actions = [
    aws_appautoscaling_policy.initial-by-queue-metric.arn,
    aws_appautoscaling_policy.scaling-high-by-queue-metric.arn
  ]

  metric_query {
    id          = "pendingMessage"
    expression  = "visible - (taskCount * ${var.MESSAGE_PER_TASK} - notVisible)"
    label       = "visible - (taskCount * ${var.MESSAGE_PER_TASK} - notVisible)"
    return_data = "true"
  }

  metric_query {
    id = "visible"

    metric {
      metric_name = "ApproximateNumberOfMessagesVisible"
      namespace   = "AWS/SQS"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        QueueName = var.MAIL_SERVICE_QUEUE_NAME
      }
    }
  }

  metric_query {
    id = "notVisible"

    metric {
      metric_name = "ApproximateNumberOfMessagesNotVisible"
      namespace   = "AWS/SQS"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        QueueName = var.MAIL_SERVICE_QUEUE_NAME
      }
    }
  }

  metric_query {
    id = "taskCount"

    metric {
      metric_name = "RunningTaskCount"
      namespace   = "ECS/ContainerInsights"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        ClusterName = var.ECS_CLUSTER_NAME
        ServiceName = var.SERVICE_NAME
      }
    }
  }
  tags = var.TAGS
}
