package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.google.gson.annotations.Expose
import traveloffice.mail.common.Constant.Companion.BLANK_MESSAGE
import traveloffice.mail.common.Constant.Companion.DEFAULT_ENCODING
import traveloffice.mail.common.Constant.Companion.REQUIRED_MESSAGE
import java.io.InputStream
import javax.validation.constraints.NotBlank

data class SendAttachmentPayload (
    @field:NotBlank(message = BLANK_MESSAGE)
    val name: String,

    @field:NotBlank(message = BLANK_MESSAGE)
    val mimeType: String,

    @field:NotBlank(message = BLANK_MESSAGE)
    val path: String,

    @field:NotBlank(message = BLANK_MESSAGE)
    var encoding: String = DEFAULT_ENCODING
)
