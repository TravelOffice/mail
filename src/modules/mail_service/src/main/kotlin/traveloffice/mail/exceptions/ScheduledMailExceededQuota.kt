package traveloffice.mail.exceptions

import traveloffice.mail.common.Constant

class ScheduledMailExceededQuota(message: String) : ResponseException(Constant.ERROR_EXCEEDED_SERVICE_QUOTA, message)