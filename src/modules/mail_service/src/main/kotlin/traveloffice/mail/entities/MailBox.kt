package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.google.gson.annotations.Expose
import traveloffice.mail.common.Constant.Companion.MAILBOX_INDEX

@DynamoDBTable(tableName = "mail_table")
class MailBox : BaseModel() {
    val name: String
        @DynamoDBIgnore
        get() {
            return this.sk.substring(MAILBOX_INDEX.length + 1)
        }

    @Expose
    @DynamoDBAttribute(attributeName = "count")
    var count: Int = 0

    @Expose
    @DynamoDBAttribute(attributeName = "unread")
    var unread: Int = 0

    @Expose
    @DynamoDBAttribute(attributeName = "size")
    var size: Long = 0

    @DynamoDBAttribute(attributeName = "syncStatus")
    var syncStatus: String? = null

    @Expose
    @DynamoDBAttribute(attributeName = "order")
    var order: Int = 0
    /**
     * Returns json object with only exposed values
     */
    override fun toJson(): MutableMap<String, Any> {
        // Get restricted json data
        val json = super.toJson()
        // Additional values
        json["name"] = this.name
        return json
    }
}

