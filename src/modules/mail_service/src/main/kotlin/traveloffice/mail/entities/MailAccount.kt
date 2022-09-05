package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import traveloffice.mail.common.Constant.Companion.DEFAULT_NAME_IS_REQUIRED
import java.util.*
import javax.validation.constraints.NotBlank

@DynamoDBTable(tableName="mail_table")
class MailAccount: BaseModel() {
    lateinit var email: String
        @DynamoDBAttribute(attributeName = "email")
        get
    lateinit var name: String
        @DynamoDBAttribute(attributeName = "name")
        get
    @NotBlank(message = DEFAULT_NAME_IS_REQUIRED)
    lateinit var authMethod: String
        @DynamoDBAttribute(attributeName = "authMethod")
        get
    lateinit var inboundType: String
        @DynamoDBAttribute(attributeName = "inboundType")
        get
    lateinit var inboundHost: String
        @DynamoDBAttribute(attributeName = "inboundHost")
        get
    lateinit var inboundPort: String
        @DynamoDBAttribute(attributeName = "inboundPort")
        get
    lateinit var inboundSslMode: String
        @DynamoDBAttribute(attributeName = "inboundSslMode")
        get
    lateinit var inboundUser: String
        @DynamoDBAttribute(attributeName = "inboundUser")
        get
    lateinit var inboundPassword: String
        @DynamoDBAttribute(attributeName = "inboundPassword")
        get
    lateinit var outboundHost: String
        @DynamoDBAttribute(attributeName = "outboundHost")
        get
    lateinit var outboundPort: String
        @DynamoDBAttribute(attributeName = "outboundPort")
        get
    lateinit var outboundSslMode: String
        @DynamoDBAttribute(attributeName = "outboundSslMode")
        get
    lateinit var outboundUser: String
        @DynamoDBAttribute(attributeName = "outboundUser")
        get
    lateinit var outboundPassword: String
        @DynamoDBAttribute(attributeName = "outboundPassword")
        get
    var signature: String? = null
        @DynamoDBAttribute(attributeName = "signature")
        get
    var lastMailboxSync: Long? = null
        @DynamoDBAttribute(attributeName = "lastMailboxSync")
        get
    lateinit var syncStatus: String
        @DynamoDBAttribute(attributeName = "syncStatus")
        get
}

