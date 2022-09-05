package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "mail_table")
class MailBox {
    lateinit var name: String
        @DynamoDBAttribute(attributeName = "name")
        get
    var messages: Int = 0
        @DynamoDBAttribute(attributeName = "messages")
        get
    var unSeenMessages: Int = 0
        @DynamoDBAttribute(attributeName = "unSeenMessages")
        get
}