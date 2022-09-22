package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import javax.mail.Flags
import javax.mail.Flags.Flag
import javax.mail.internet.InternetAddress

data class SendMailRequestPayload(
    val subject: String,
    val htmlContent: String,
    val from: List<EmailAddress>,
    val to: List<EmailAddress>,
    var cc: List<EmailAddress>,
    var bcc: List<EmailAddress>,
    var attachments: List<String>
)
