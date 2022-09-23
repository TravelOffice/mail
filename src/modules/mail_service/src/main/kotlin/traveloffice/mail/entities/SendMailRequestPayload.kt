package traveloffice.mail.entities

data class SendMailRequestPayload(
    val subject: String,
    val htmlContent: String,
    val from: List<EmailAddress>,
    val to: List<EmailAddress>,
    var cc: List<EmailAddress>,
    var bcc: List<EmailAddress>,
    var attachments: List<String>
)

