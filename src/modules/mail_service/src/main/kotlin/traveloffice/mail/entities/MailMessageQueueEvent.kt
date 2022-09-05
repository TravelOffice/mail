package traveloffice.mail.entities

data class MailMessageQueueEvent(
    var messageId: String,
    var receiptHandle: String,
    var data: MailMessageQueueData?
)
