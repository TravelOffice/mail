package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import javax.mail.Flags
import javax.mail.Flags.Flag

@DynamoDBTable(tableName = "mail_table")
class MailMessage : BaseModel() {
    @DynamoDBAttribute(attributeName = "messageId")
    lateinit var messageId: String

    @DynamoDBAttribute(attributeName = "subject")
    lateinit var subject: String

    @DynamoDBAttribute(attributeName = "sentDate")
    var sentDate: Long? = null

    @DynamoDBAttribute(attributeName = "receivedDate")
    var receivedDate: Long? = null

    @DynamoDBAttribute(attributeName = "briefContent")
    lateinit var briefContent: String

    @DynamoDBAttribute(attributeName = "htmlContent")
    lateinit var htmlContent: String

    @DynamoDBAttribute(attributeName = "flags")
    var flags: String = Flags(Flag.RECENT).toString()

    @DynamoDBAttribute(attributeName = "from")
    var from: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "to")
    var to: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "cc")
    var cc: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "bcc")
    var bcc: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "attachments")
    var attachments: List<MailAttachment>? = null

    @DynamoDBAttribute(attributeName = "size")
    var size: Int = 0

}
