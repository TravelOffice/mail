package traveloffice.mail.common

class Constant {
    companion object {
        const val MAIL_INDEX = "MAIL"
        const val TENANT_INDEX = "TENANT"
        const val MAILBOX_INDEX = "MAILBOX"
        const val MAIL_ACCOUNT_INDEX = "MAIL_ACCOUNT"
        const val USER_INDEX = "USER"
        const val MESSAGE_INDEX = "MESSAGE"
        const val SUCCESS_INDEX = "SUCCESS"
        const val FAILED_INDEX = "FAILED"
        const val UID_INDEX = "UID"
        const val SEARCH_INDEX = "SEARCH"
        const val SEARCH_API_KEY_INDEX = "MAIL_API_KEY";
        const val CODE_VERIFIER_INDEX = "CODE_VERIFIER"
        const val DEFAULT_MAX_NUMBER_MESSAGE = 5
        const val DEFAULT_WAIT_TIME_SECOND = 5
        const val DEFAULT_VISIBLE_TIME_OUT = 3600
        const val DEFAULT_PRESIGNED_URL_EXPIRATION: Int = 5
        const val DEFAULT_MAX_THREAD_POOL = 5
        const val DEFAULT_MAIL_TIMEOUT = 5000
        const val DEFAULT_NUMBER_OF_MAILS_TO_FETCH = 10
        const val DEFAULT_NUMBER_OF_MAILS_TO_SYNC = 100L
        const val MAX_NUMBER_LAST_SYNC_STATUS_COUNT = 5L
        const val MAX_BRIEF_ADDRESS_SIZE = 3
        const val DEFAULT_ENCODING = "UTF-8"
        const val COGNITO_USERNAME_DELIMITER = "#"
        const val ENCRYPT_SECRET_KEY = "ENCRYPT_SECRET_KEY"
        const val REQUIRED_MESSAGE = "is required"
        const val ONE_OF_MESSAGE = "should be one "
        const val INVALID_MESSAGE = "is invalid"
        const val GREATER_THAN_EQUAL_MESSAGE = "should be grater than or equal to"
        const val LESS_THAN_EQUAL_MESSAGE = "should be less than or equal to"
        const val BLANK_MESSAGE = "cannot be blank"
        const val MAIL_NEW_EMAIL_RECEIVED = "MAIL_NEW_EMAIL_RECEIVED"
        const val MAIL_BUCKET = "MAIL_BUCKET"
        const val S3_TEMPORARY_UPLOAD_FOLDER = "temporary-upload"
        const val MAIL_CDN = "MAIL_CDN"
        val DEFAULT_MAILBOX = arrayOf("INBOX", "SENT", "DRAFT", "DELETE", "SPAM")
        const val TESTING_MAIL_SUBJECT = "iTAB Test Message"
        const val TESTING_MAIL_CONTEXT = "This is an e-mail message sent automatically by iTAB while testing the settings for your account."
        const val ERROR_VALIDATOR = "ERROR_VALIDATOR"
        const val ERROR_DUPLICATED_MAIL_ACCOUNT = "ERROR_DUPLICATED_MAIL_ACCOUNT"
        const val ERROR_CONNECT_TO_INBOUND_SERVER = "ERROR_CONNECT_TO_INBOUND_SERVER"
        const val ERROR_LOGIN_TO_INBOUND_SERVER = "ERROR_LOGIN_TO_INBOUND_SERVER"
        const val ERROR_CONNECT_TO_OUTBOUND_SERVER = "ERROR_CONNECT_TO_OUTBOUND_SERVER"
        const val ERROR_LOGIN_TO_OUTBOUND_SERVER = "ERROR_LOGIN_TO_OUTBOUND_SERVER"
        const val ERROR_EMAIL_ADDRESS_IS_INVALID = "ERROR_EMAIL_ADDRESS_IS_INVALID"
        const val ERROR_DOWNLOAD_ATTACHMENT = "ERROR_DOWNLOAD_ATTACHMENT"
        const val ERROR_MAIL_PROVIDER_NOT_SUPPORTED = "ERROR_MAIL_PROVIDER_NOT_SUPPORTED"
        const val ERROR_UNKNOWN = "ERROR_UNKNOWN"
        const val DEFAULT_MAILBOX_INBOX = "INBOX"
        const val DEFAULT_MAILBOX_SENT = "SENT"
        const val DEFAULT_MAILBOX_DRAFT = "DRAFT"
        const val DEFAULT_MAILBOX_SPAM = "SPAM"
        const val DEFAULT_MAILBOX_DELETE = "DELETE"
    }
}
