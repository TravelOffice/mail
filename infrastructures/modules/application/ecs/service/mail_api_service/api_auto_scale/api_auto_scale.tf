variable "MIN_CAPACITY" {}
variable "MAX_CAPACITY" {}
variable "SERVICE_NAME" {}
variable "ECS_CLUSTER_NAME" {}
variable "TAGS" {}

resource "aws_appautoscaling_target" "target" {
  max_capacity       = var.MAX_CAPACITY
  min_capacity       = var.MIN_CAPACITY
  resource_id        = "service/${var.ECS_CLUSTER_NAME}/${var.SERVICE_NAME}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu_auto_scaling" {
  depends_on         = [aws_appautoscaling_target.target]
  name               = "CPU Auto Scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.target.resource_id
  scalable_dimension = aws_appautoscaling_target.target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.target.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }

    target_value       = 75
    scale_in_cooldown  = 300
    scale_out_cooldown = 300
  }
}
