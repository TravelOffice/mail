package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import traveloffice.mail.extensions.toCDNUrl
import java.io.InputStream
import javax.mail.Flags
import javax.mail.Message
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

    @DynamoDBIgnore
    var plainTextEmailBody: InputStream? = null

    @Expose
    @DynamoDBAttribute(attributeName = "htmlContent")
    var htmlContent: String = ""

    @DynamoDBIgnore
    var rawHtmlContent: MailAttachment? = null

    @Expose
    @DynamoDBAttribute(attributeName = "metaData")
    var metaData: String = ""

    @DynamoDBTypeConverted(converter = FlagTypeConverter::class)
    @DynamoDBAttribute(attributeName = "flags")
    var flags: Flags = Flags(Flags.Flag.RECENT)

    @Expose
    @DynamoDBAttribute(attributeName = "from")
    var from: List<EmailAddress> = emptyList()

    @Expose
    @DynamoDBAttribute(attributeName = "to")
    var to: BriefAddress = BriefAddress()

    @Expose
    @DynamoDBAttribute(attributeName = "cc")
    var cc: BriefAddress? = null

    @Expose
    @DynamoDBAttribute(attributeName = "bcc")
    var bcc: BriefAddress? = null

    @Expose
    @DynamoDBAttribute(attributeName = "inReplyTo")
    var inReplyTo: String? = null

    @Expose
    @DynamoDBAttribute(attributeName = "attachments")
    var attachments: List<MailAttachment>? = null

    @Expose
    @DynamoDBAttribute(attributeName = "size")
    var size: Long = 0

    @DynamoDBIgnore
    var raw: Message? = null

    @DynamoDBAttribute(attributeName = "uid")
    var uid: String? = null

    val fromAsInternetAddress: InternetAddress
        @DynamoDBIgnore
        get() {
            return this.from.first().toInternetAddress()
        }

    val toAsInternetAddresses: List<InternetAddress>
        @DynamoDBIgnore
        get() {
            return this.to.rawAddress.map {
                it.toInternetAddress()
            }
        }
    val ccAsInternetAddresses: List<InternetAddress>
        @DynamoDBIgnore
        get() {
            return this.cc?.rawAddress?.map {
                it.toInternetAddress()
            } ?: emptyList()
        }

    val bccAsInternetAddresses: List<InternetAddress>
        @DynamoDBIgnore
        get() {
            return this.bcc?.rawAddress?.map {
                it.toInternetAddress()
            } ?: emptyList()
        }


    fun clone(): MailMessage {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        val jsonString = gson.toJson(this, MailMessage::class.java)
        val clone = gson.fromJson(jsonString, MailMessage::class.java)
        clone.to = Gson().fromJson(Gson().toJson(this.to, BriefAddress::class.java), BriefAddress::class.java)
        clone.cc = Gson().fromJson(Gson().toJson(this.cc, BriefAddress::class.java), BriefAddress::class.java)
        clone.bcc = Gson().fromJson(Gson().toJson(this.bcc, BriefAddress::class.java), BriefAddress::class.java)
        return clone
    }

    /**
     * Returns json object with only exposed values
     */
    override fun toJson(): MutableMap<String, Any> {
        // Get restricted json data
        val json = super.toJson()
        // Additional values
        json["flags"] = this.flags.toString()
        return json
    }

    fun toJson(CDN: String): Map<String, Any> {
        val newMessage = this.clone()
        newMessage.htmlContent = newMessage.htmlContent.toCDNUrl(CDN)
        newMessage.metaData = newMessage.metaData.toCDNUrl(CDN)
        newMessage.attachments = newMessage.attachments?.map {
            it.path = it.path.toCDNUrl(CDN)
            return@map it
        }
        return newMessage.toJson()
    }

    // Converts the complex type FlagType to a string and vice-versa.
    class FlagTypeConverter : DynamoDBTypeConverter<String?, Flags?> {
        override fun convert(`object`: Flags?): String? {
            val itemFlags: Flags? = `object`
            var flags: String? = null
            try {
                if (itemFlags != null) {
                    flags = Gson().toJson(itemFlags)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return flags
        }

        override fun unconvert(s: String?): Flags {
            var itemFlags = Flags()
            try {
                if (!s.isNullOrEmpty()) {
                    itemFlags = Gson().fromJson(s, Flags::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return itemFlags
        }
    }
}

