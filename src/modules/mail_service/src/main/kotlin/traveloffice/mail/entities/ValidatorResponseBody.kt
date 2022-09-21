package traveloffice.mail.entities

class ValidatorResponseBody(
    override val code: String,
    override val message: String,
    override val data: Any?,
    val errors: Map<String, String?>
): ResponseBody<Any>(code, message, data)
