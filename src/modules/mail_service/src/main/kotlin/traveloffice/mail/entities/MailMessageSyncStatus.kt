package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.google.gson.annotations.Expose
import traveloffice.mail.common.Constant.Companion.SUCCESS_INDEX

@DynamoDBTable(tableName = "mail_table")
class MailMessageSyncStatus : BaseModel() {

    @DynamoDBAttribute(attributeName = "uid")
    lateinit var uid: String

    @DynamoDBIgnore
    var messageNumber: Int = 0

    @DynamoDBAttribute(attributeName = "syncTime")
    var syncTime: Long? = null

    @DynamoDBIgnore
    var message: MailMessage? = null

    @DynamoDBIgnore
    var failed: Boolean = false

    @DynamoDBIgnore
    var size: Long = 0

    @DynamoDBAttribute(attributeName = "errorMessage")
    var errorMessage: String? = null
}
