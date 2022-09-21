package traveloffice.mail.entities

open class ResponseBody<T>(
    open val code: String,
    open val message: String,
    open val data: T? = null,
)