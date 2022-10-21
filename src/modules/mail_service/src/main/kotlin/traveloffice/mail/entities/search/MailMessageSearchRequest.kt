package traveloffice.mail.entities.search

data class MailMessageSearchRequest(
    val q: String,
    val offset: Int? = null,
    val limit: Int? = null,
    val attributesToRetrieve: List<String> = listOf("*"),
    val sort: List<String>? = null,
    val filter: List<String>? = null,
)
