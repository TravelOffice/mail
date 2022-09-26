package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted
import com.google.gson.Gson
import javax.mail.Flags
import javax.mail.Flags.Flag

@DynamoDBTable(tableName = "mail_table")
class MailMessageUpdateRequest : BaseModel() {
    @DynamoDBAttribute(attributeName = "messageId")
    var messageId: String? = null

    @DynamoDBAttribute(attributeName = "subject")
    var subject: String? = null
    
    @DynamoDBAttribute(attributeName = "sentDate")
    var sentDate: Long? = null

    @DynamoDBAttribute(attributeName = "receivedDate")
    var receivedDate: Long? = null

    @DynamoDBAttribute(attributeName = "briefContent")
    var briefContent: String? = null

    @DynamoDBAttribute(attributeName = "htmlContent")
    var htmlContent: String? = null

    @DynamoDBTypeConverted(converter = MailMessage.FlagTypeConverter::class)
    @DynamoDBAttribute(attributeName = "flags")
    var flags: Flags? = null

    @DynamoDBAttribute(attributeName = "from")
    var from: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "to")
    var to: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "cc")
    var cc: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "bcc")
    var bcc: List<EmailAddress>? = null

    @DynamoDBAttribute(attributeName = "inReplyTo")
    var inReplyTo: String? = null

    @DynamoDBAttribute(attributeName = "attachments")
    var attachments: List<MailAttachment>? = null

    @DynamoDBAttribute(attributeName = "size")
    var size: Int? = null

    class Builder {
        private var mailMessageUpdateRequest = MailMessageUpdateRequest()

        fun withHashKey(pk: String): Builder {
            this.mailMessageUpdateRequest.pk = pk
            return this
        }

        fun withRangeKey(sk: String): Builder {
            this.mailMessageUpdateRequest.sk = sk
            return this
        }

        fun build(): MailMessageUpdateRequest {
            return mailMessageUpdateRequest
        }
    }
}
