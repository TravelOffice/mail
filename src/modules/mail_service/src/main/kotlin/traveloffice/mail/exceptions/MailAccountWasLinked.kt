package traveloffice.mail.exceptions

import traveloffice.mail.common.Constant.Companion.ERROR_DUPLICATED_MAIL_ACCOUNT

class MailAccountWasLinked(message: String) : ResponseException(message, ERROR_DUPLICATED_MAIL_ACCOUNT)