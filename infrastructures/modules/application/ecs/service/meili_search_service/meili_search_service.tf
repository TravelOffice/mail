variable "MIN_CAPACITY" {}
variable "MAX_CAPACITY" {}
variable "SERVICE_NAME" {}
variable "DESIRED_COUNT" {}
variable "SEARCH_TARGET_GROUP_ARN" {}
variable "SUBNETS" {}
variable "AWS_REGION" {}
variable "VPC_ID" {}
variable "ENV" {}
variable "FEATURE_NAME" {}
variable "TAGS" {}
variable "SEARCH_DATA_VOLUME" {}
variable "INSTANCE_ID" {
  default = "ami-07651f0c4c315a529"
}
variable "MEILI_SEARCH_MASTER_KEY" {}


// Create the meli search service
locals {
  volume_id = var.SEARCH_DATA_VOLUME
  user_data = templatefile(
    format("%s/%s", path.module, "user_data.tftpl"),
    {
      volume_id  = local.volume_id,
      region     = var.AWS_REGION,
      master_key = var.MEILI_SEARCH_MASTER_KEY,
      env        = var.ENV == "dev" ? "development" : "production",
      log_level  = var.ENV == "dev" ? "INFO" : "WARN",
      log_group  = substr(data.aws_arn.task_log.resource, length("log-group:"), -1)
    }
  )
  instance_type = var.ENV == "dev" ? "t2.micro" : "t3.medium"
}

resource "aws_iam_instance_profile" "profile" {
  name_prefix = "${var.SERVICE_NAME}-Instance-Profile"
  role        = aws_iam_role.role.name
}

resource "aws_cloudwatch_log_group" "task_log" {
  name_prefix       = "mail/search"
  retention_in_days = var.ENV == "dev" ? 1 : 7

  tags = var.TAGS
}

data "aws_arn" "task_log" {
  arn = aws_cloudwatch_log_group.task_log.arn
}

resource "aws_iam_policy" "policy" {
  name_prefix = lower("${var.SERVICE_NAME}_policy")

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
          "ec2:AttachVolume",
          "ec2:DetachVolume"
        ],
        "Resource" : [
          "arn:aws:ec2:*:*:instance/*",
          "arn:aws:ec2:*:*:volume/${local.volume_id}"
        ]
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "ec2:DescribeVolumes",
          "ec2:DescribeTags"
        ],
        "Resource" : "*"
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ],
        "Resource" : [
          "*"
        ]
      },
    ]
  })

  tags = var.TAGS
}

resource "aws_iam_role" "role" {
  name_prefix = "${var.SERVICE_NAME}-Instance-Role"
  tags        = var.TAGS
  path        = "/"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
    ]
  })
  managed_policy_arns = [
    aws_iam_policy.policy.arn,
    "arn:aws:iam::aws:policy/AmazonSSMFullAccess"
  ]
}

resource "tls_private_key" "this" {
  algorithm = "RSA"
}

module "KEY_PAIR" {
  source  = "terraform-aws-modules/key-pair/aws"
  version = "2.0.0"

  key_name           = "${var.SERVICE_NAME}-keypair"
  create_private_key = true
  public_key         = trimspace(tls_private_key.this.public_key_openssh)
  tags               = var.TAGS
}

module "ECS_ASG" {
  source  = "terraform-aws-modules/autoscaling/aws"
  version = "6.5.2"

  # Autoscaling group
  name = "${var.SERVICE_NAME}-ASG"

  min_size                  = var.MIN_CAPACITY
  max_size                  = var.MAX_CAPACITY
  desired_capacity          = var.DESIRED_COUNT
  wait_for_capacity_timeout = 0
  health_check_type         = "ELB"
  health_check_grace_period = 300
  security_groups           = [aws_security_group.search.id]
  target_group_arns         = [var.SEARCH_TARGET_GROUP_ARN]
  vpc_zone_identifier       = [var.SUBNETS[0]]

  # Launch template
  launch_template_name        = "${var.SERVICE_NAME}-template"
  launch_template_description = "${var.SERVICE_NAME} Launch template"
  update_default_version      = true

  image_id      = var.INSTANCE_ID
  instance_type = local.instance_type
  user_data     = base64encode(local.user_data)
  key_name      = module.KEY_PAIR.key_pair_name

  create_iam_instance_profile = false
  iam_instance_profile_arn    = aws_iam_instance_profile.profile.arn

  block_device_mappings = [
    {
      # Root volume
      device_name = "/dev/sda1"
      no_device   = 0
      ebs = {
        delete_on_termination = true
        encrypted             = true
        volume_size           = 8
        volume_type           = "gp3"
      }
    }
  ]

  instance_market_options = {
    market_type = "spot"
  }
}


resource "aws_security_group" "search" {
  name_prefix = "ecs-mail-sg"
  description = "Allow all outbound traffic"
  vpc_id      = var.VPC_ID

  ingress {
    description      = "LB to ECS Task"
    from_port        = 80
    to_port          = 80
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
