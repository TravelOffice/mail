package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import java.io.InputStream

@DynamoDBDocument
class MailAttachment {
    @DynamoDBAttribute(attributeName = "name")
    lateinit var name: String

    @DynamoDBAttribute(attributeName = "mimeType")
    lateinit var mimeType: String

    @DynamoDBAttribute(attributeName = "path")
    lateinit var path: String

    @DynamoDBAttribute(attributeName = "size")
    var size: Int = 0

    @DynamoDBAttribute(attributeName = "encoding")
    lateinit var encoding: String

    @DynamoDBIgnore
    lateinit var inputStream: InputStream

}
