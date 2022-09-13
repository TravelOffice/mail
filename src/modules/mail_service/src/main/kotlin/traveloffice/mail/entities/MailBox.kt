package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import traveloffice.mail.common.Constant.Companion.MAILBOX_INDEX

@DynamoDBTable(tableName = "mail_table")
class MailBox : BaseModel() {
    val name: String
        @DynamoDBIgnore
        get() {
            return this.sk.substring(MAILBOX_INDEX.length + 1)
        }
    @DynamoDBAttribute(attributeName = "count")
    var count: Int? = null

    @DynamoDBAttribute(attributeName = "unread")
    var unread: Int? = null

    @DynamoDBAttribute(attributeName = "size")
    var size: Long? = null

    @DynamoDBAttribute(attributeName = "lastMailboxSync")
    var lastMailboxSync: Long? = null

    @DynamoDBAttribute(attributeName = "syncStatus")
    var syncStatus: String? = null

}
