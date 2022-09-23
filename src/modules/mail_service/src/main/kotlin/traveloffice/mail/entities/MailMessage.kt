package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import javax.mail.Flags
import javax.mail.Flags.Flag
import javax.mail.internet.InternetAddress

@DynamoDBTable(tableName = "mail_table")
class MailMessage : BaseModel() {
    @Expose
    @DynamoDBAttribute(attributeName = "messageId")
    lateinit var messageId: String

    @Expose
    @DynamoDBAttribute(attributeName = "subject")
    lateinit var subject: String

    @Expose
    @DynamoDBAttribute(attributeName = "sentDate")
    var sentDate: Long? = null

    @Expose
    @DynamoDBAttribute(attributeName = "receivedDate")
    var receivedDate: Long? = null

    @Expose
    @DynamoDBAttribute(attributeName = "briefContent")
    lateinit var briefContent: String

    @Expose
    @DynamoDBAttribute(attributeName = "htmlContent")
    lateinit var htmlContent: String

    @Expose
    @DynamoDBAttribute(attributeName = "flags")
    var flags: String = Flags(Flag.RECENT).toString()

    @Expose
    @DynamoDBAttribute(attributeName = "from")
    var from: List<EmailAddress> = emptyList()

    @Expose
    @DynamoDBAttribute(attributeName = "to")
    var to: List<EmailAddress> = emptyList()

    @Expose
    @DynamoDBAttribute(attributeName = "cc")
    var cc: List<EmailAddress>? = null

    @Expose
    @DynamoDBAttribute(attributeName = "bcc")
    var bcc: List<EmailAddress>? = null

    @Expose
    @DynamoDBAttribute(attributeName = "inReplyTo")
    var inReplyTo: String? = null

    @Expose
    @DynamoDBAttribute(attributeName = "attachments")
    var attachments: List<MailAttachment>? = null

    @Expose
    @DynamoDBAttribute(attributeName = "size")
    var size: Int = 0

    val fromAsInternetAddress: InternetAddress
        @DynamoDBIgnore
        get() {
            return this.from.first().toInternetAddress()
        }

    val toAsInternetAddresses: List<InternetAddress>
        @DynamoDBIgnore
        get() {
            return this.to.map {
                it.toInternetAddress()
            }
        }

    /**
     * Returns json object with only exposed values
     */
    override fun restrict(): MutableMap<String, Any> {
        // Get restricted json data
        return super.restrict()
    }

    private fun clone(): MailMessage {
        val stringAnimal = Gson().toJson(this, MailMessage::class.java)
        return Gson().fromJson(stringAnimal, MailMessage::class.java)
    }

    fun restrict(CDN: String): Map<String, Any> {
        val restrictMessage = this.clone()
        restrictMessage.htmlContent = "$CDN/${restrictMessage.htmlContent}"
        restrictMessage.attachments = restrictMessage.attachments?.map {
            it.path = "$CDN/${it.path}"
            return@map it
        }
        return restrictMessage.restrict()
    }
}
