variable "schedule_backup_plan" {
  default     = "cron(0 17 ? * * *)"
  description = "Set time for backup plan. Default value is 17:00 UTC daily as 00h00 Vietnam Time"
}
variable "backup_selection_resource" {
  description = "Define backup_selection_tag"
  default = {
    type  = "STRINGEQUALS"
    key   = "Name"
    value = "mail-search-ebs"
  }
}
