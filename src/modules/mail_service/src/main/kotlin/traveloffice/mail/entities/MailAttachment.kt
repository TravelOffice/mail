package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.google.gson.annotations.Expose
import traveloffice.mail.common.Constant.Companion.DEFAULT_ENCODING
import java.io.InputStream

@DynamoDBDocument
class MailAttachment {
    @Expose
    @DynamoDBAttribute(attributeName = "name")
    lateinit var name: String

    @Expose
    @DynamoDBAttribute(attributeName = "mimeType")
    lateinit var mimeType: String

    @Expose
    @DynamoDBAttribute(attributeName = "path")
    lateinit var path: String

    @Expose
    @DynamoDBAttribute(attributeName = "size")
    var size: Long = 0

    @Expose
    @DynamoDBAttribute(attributeName = "encoding")
    var encoding: String = DEFAULT_ENCODING

    @DynamoDBIgnore
    lateinit var inputStream: InputStream
}
