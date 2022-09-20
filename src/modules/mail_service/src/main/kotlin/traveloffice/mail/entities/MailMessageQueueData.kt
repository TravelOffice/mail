package traveloffice.mail.entities

data class MailMessageQueueData(
    var tenant: String,
    var username: String,
    var action: String,
    var arguments: Map<String, Any>
)
