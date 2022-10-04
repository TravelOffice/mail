package traveloffice.mail.exceptions

import traveloffice.mail.common.Constant.Companion.ERROR_MAIL_PROVIDER_NOT_SUPPORTED

class UnSupportedProvider(message: String) : ResponseException(message, ERROR_MAIL_PROVIDER_NOT_SUPPORTED)
