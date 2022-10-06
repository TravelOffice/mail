package traveloffice.mail.exceptions

import traveloffice.mail.common.Constant.Companion.ERROR_DOWNLOAD_ATTACHMENT

class S3DownloadFileException(message: String) : ResponseException(ERROR_DOWNLOAD_ATTACHMENT, message)
