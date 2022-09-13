package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import traveloffice.mail.common.Constant.Companion.BLANK_MESSAGE
import traveloffice.mail.common.Constant.Companion.GREATER_THAN_EQUAL_MESSAGE
import traveloffice.mail.common.Constant.Companion.INVALID_MESSAGE
import traveloffice.mail.common.Constant.Companion.LESS_THAN_EQUAL_MESSAGE
import traveloffice.mail.common.Constant.Companion.ONE_OF_MESSAGE
import traveloffice.mail.common.Constant.Companion.REQUIRED_MESSAGE
import traveloffice.mail.validators.OneOf
import javax.validation.constraints.Email
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

@DynamoDBTable(tableName="mail_table")
class MailAccount: BaseModel() {
    @DynamoDBAttribute(attributeName = "email")
    @Email(message = INVALID_MESSAGE)
    lateinit var email: String

    @DynamoDBAttribute(attributeName = "name")
    @NotBlank(message = REQUIRED_MESSAGE)
    lateinit var name: String

    @DynamoDBAttribute(attributeName = "protocol")
    @OneOf(array = ["imap", "pop3"], message = "$ONE_OF_MESSAGE [imap, pop3]")
    lateinit var protocol: String

    val inboundType: String
        @DynamoDBIgnore
        get() {
            return if (inboundSslMode == "ssl") "${protocol}s"
                else protocol
        }

    @DynamoDBAttribute(attributeName = "inboundHost")
    @NotBlank(message = REQUIRED_MESSAGE)
    lateinit var inboundHost: String

    @DynamoDBAttribute(attributeName = "inboundPort")
    @Min(value = 25, message = "$GREATER_THAN_EQUAL_MESSAGE 25")
    @Max(value = 995, message = "$LESS_THAN_EQUAL_MESSAGE 995")
    var inboundPort: Int = 993

    @DynamoDBAttribute(attributeName = "inboundSslMode")
    @OneOf(array = ["none", "ssl", "tls"], message = "$ONE_OF_MESSAGE [none, ssl, tls]")
    lateinit var inboundSslMode: String

    @DynamoDBAttribute(attributeName = "inboundUser")
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var inboundUser: String

    @DynamoDBAttribute(attributeName = "inboundPassword")
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var inboundPassword: String

    @DynamoDBAttribute(attributeName = "outboundHost")
    @NotBlank(message = REQUIRED_MESSAGE)
    lateinit var outboundHost: String

    @DynamoDBAttribute(attributeName = "outboundPort")
    @Min(value = 25, message = "$GREATER_THAN_EQUAL_MESSAGE 25")
    @Max(value = 995, message = "$LESS_THAN_EQUAL_MESSAGE 995")
    var outboundPort: Int = 25

    @DynamoDBAttribute(attributeName = "outboundSslMode")
    @OneOf(array = ["none", "ssl", "tls"], message = "$ONE_OF_MESSAGE [none, ssl, tls]")
    lateinit var outboundSslMode: String

    @DynamoDBAttribute(attributeName = "outboundUser")
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var outboundUser: String

    @DynamoDBAttribute(attributeName = "outboundPassword")
    @NotBlank(message = BLANK_MESSAGE)
    lateinit var outboundPassword: String

    @DynamoDBAttribute(attributeName = "signature")
    var signature: String? = null

    @DynamoDBAttribute(attributeName = "provider")
    @OneOf(array = ["Gmail", "Outlook", "iCloud", "Yahoo", "Other"], message = "$ONE_OF_MESSAGE [Gmail, Outlook, iCloud, Yahoo, Other]")
    lateinit var provider: String

    @DynamoDBIgnore
    lateinit var tenant: String
    @DynamoDBIgnore
    lateinit var username: String
}
