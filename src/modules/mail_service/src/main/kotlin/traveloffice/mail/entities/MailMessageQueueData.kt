package traveloffice.mail.entities

data class MailMessageQueueData(
    var tenant: String,
    var username: String,
    var action: String,
    var data: Map<String, Any>
)
