package traveloffice.mail.exceptions

import java.lang.Exception

open class ResponseException(
    override val message: String,
    val code: String
) : Exception(message)
