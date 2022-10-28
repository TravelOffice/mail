package traveloffice.mail.exceptions

open class ResponseException(
    override val message: String,
    val code: String
) : Exception(message)
