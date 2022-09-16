package traveloffice.mail.entities

data class ResponseBody<T>(
    val code: String,
    val message: String,
    val data: T? = null,
)