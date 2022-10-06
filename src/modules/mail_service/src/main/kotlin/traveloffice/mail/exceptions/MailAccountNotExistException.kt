package traveloffice.mail.exceptions

import traveloffice.mail.common.Constant.Companion.ERROR_DOWNLOAD_ATTACHMENT

class MailAccountNotExistException(message: String) : ResponseException(ERROR_DOWNLOAD_ATTACHMENT, message)
