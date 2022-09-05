package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import java.util.*

@DynamoDBTable(tableName = "mail_table")
class MailMessage : BaseModel() {
    lateinit var messageId: String
        @DynamoDBAttribute(attributeName = "messageId")
        get
    lateinit var subject: String
        @DynamoDBAttribute(attributeName = "subject")
        get
    var sendAt: Long = Date().time
        @DynamoDBAttribute(attributeName = "sendAt")
        get
    var flagSeen: Boolean = false
        @DynamoDBAttribute(attributeName = "flagSeen")
        get
}