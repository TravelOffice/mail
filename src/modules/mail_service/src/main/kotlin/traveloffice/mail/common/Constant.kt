package traveloffice.mail.common

class Constant {
    companion object {
        const val TENANT_INDEX = "TENANT"
        const val MAILBOX_INDEX = "MAILBOX"
        const val MAIL_ACCOUNT_INDEX = "MAIL_ACCOUNT"
        const val USER_INDEX = "USER"
        const val MESSAGE_INDEX = "MESSAGE"
        const val DEFAULT_MAX_NUMBER_MESSAGE = 5
        const val DEFAULT_WAIT_TIME_SECOND = 5
        const val DEFAULT_MAX_NUMBER_OF_MESSAGE = 5
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
        const val MAIL_CDN = "MAIL_CDN"
        val DEFAULT_MAILBOX = arrayOf("INBOX", "SENT", "DRAFT", "DELETE", "SPAM")
        const val TESTING_MAIL_SUBJECT = "iTAB Test Message"
        const val TESTING_MAIL_CONTEXT = "This is an e-mail message sent automatically by iTAB while testing the settings for your account."

    }
}
