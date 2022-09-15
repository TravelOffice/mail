package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import traveloffice.mail.common.Constant.Companion.BLANK_MESSAGE
import traveloffice.mail.common.Constant.Companion.GREATER_THEN_EQUAL_MESSAGE
import traveloffice.mail.common.Constant.Companion.INVALID_MESSAGE
import traveloffice.mail.common.Constant.Companion.LESS_THEN_EQUAL_MESSAGE
import traveloffice.mail.common.Constant.Companion.ONE_OF_MESSAGE
import traveloffice.mail.common.Constant.Companion.REQUIRED_MESSAGE
import traveloffice.mail.validators.OneOf
import javax.validation.constraints.Email
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

@DynamoDBTable(tableName="mail_table")
class MailAccount: BaseModel() {
    @Email(message = INVALID_MESSAGE)
    lateinit var email: String
        @DynamoDBAttribute(attributeName = "email")
        get
    @NotBlank(message = REQUIRED_MESSAGE)
    lateinit var name: String
        @DynamoDBAttribute(attributeName = "name")
        get
    val authMethod: String = "username/password"
        @DynamoDBAttribute(attributeName = "authMethod")
        get
    @OneOf(array = ["imap", "pop3"], message = "$ONE_OF_MESSAGE [imap, pop3]")
    lateinit var protocol: String
        @DynamoDBAttribute(attributeName = "protocol")
        get
    val inboundType: String
        @DynamoDBAttribute(attributeName = "inboundType")
        get() {
            return if (inboundSslMode == "ssl") "${protocol}s"
                else protocol
        }
    @NotBlank(message = REQUIRED_MESSAGE)
    lateinit var inboundHost: String
        @DynamoDBAttribute(attributeName = "inboundHost")
        get

    @Min(value = 25, message = "$GREATER_THEN_EQUAL_MESSAGE 20")
    @Max(value = 995, message = "$LESS_THEN_EQUAL_MESSAGE 995")
    lateinit var inboundPort: Number
        @DynamoDBAttribute(attributeName = "inboundPort")
        get
    @OneOf(array = ["none", "ssl", "tls"], message = "$ONE_OF_MESSAGE [none, ssl, tls]")
    lateinit var inboundSslMode: String
        @DynamoDBAttribute(attributeName = "inboundSslMode")
        get
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var inboundUser: String
        @DynamoDBAttribute(attributeName = "inboundUser")
        get
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var inboundPassword: String
        @DynamoDBAttribute(attributeName = "inboundPassword")
        get
    @NotBlank(message = REQUIRED_MESSAGE)

    lateinit var outboundHost: String
        @DynamoDBAttribute(attributeName = "outboundHost")
        get
    @Min(value = 25, message = "$GREATER_THEN_EQUAL_MESSAGE 25")
    @Max(value = 995, message = "$LESS_THEN_EQUAL_MESSAGE 995")
    lateinit var outboundPort: Number
        @DynamoDBAttribute(attributeName = "outboundPort")
        get
    @OneOf(array = ["none", "ssl", "tls"], message = "$ONE_OF_MESSAGE [none, ssl, tls]")
    lateinit var outboundSslMode: String
        @DynamoDBAttribute(attributeName = "outboundSslMode")
        get
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var outboundUser: String
        @DynamoDBAttribute(attributeName = "outboundUser")
        get
    @NotBlank(message = BLANK_MESSAGE)
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
    @OneOf(array = ["Gmail", "Outlook", "iCloud", "Yahoo", "Other"], message = "$ONE_OF_MESSAGE [Gmail, Outlook, iCloud, Yahoo, Other]")
    lateinit var provider: String
        @DynamoDBAttribute(attributeName = "provider")
        get
}

