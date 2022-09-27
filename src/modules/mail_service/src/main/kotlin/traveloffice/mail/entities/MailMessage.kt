package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import javax.mail.Flags
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

    @DynamoDBTypeConverted(converter = FlagTypeConverter::class)
    @DynamoDBAttribute(attributeName = "flags")
    var flags: Flags = Flags(Flags.Flag.RECENT)

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

    fun clone(): MailMessage {
        val jsonString = Gson().toJson(this, MailMessage::class.java)
        return Gson().fromJson(jsonString, MailMessage::class.java)
    }

    private fun toJson(): Map<String, Any> {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        val mapType: Type = object : TypeToken<Map<String, Any>?>() {}.type
        val json = gson.fromJson<Map<String, Any>>(gson.toJson(this), mapType).toMutableMap()
        json["flags"] = this.flags.toString()
        return json
    }

    fun toJsonWithCDN(CDN: String): Map<String, Any> {
        val restrictMessage = this.clone()
        restrictMessage.htmlContent = "$CDN/${restrictMessage.htmlContent}"
        restrictMessage.attachments = restrictMessage.attachments?.map {
            it.path = "$CDN/${it.path}"
            return@map it
        }
        return restrictMessage.toJson()
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

