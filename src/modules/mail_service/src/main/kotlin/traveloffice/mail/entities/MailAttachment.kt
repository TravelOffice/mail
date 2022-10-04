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
    var inputStream: InputStream? = null

    class Builder {
        private var mailAttachment = MailAttachment()

        fun withName(name: String): Builder {
            this.mailAttachment.name = name
            return this
        }

        fun withMimeType(mimeType: String): Builder {
            this.mailAttachment.mimeType = mimeType
            return this
        }

        fun withEncoding(encoding: String): Builder {
            this.mailAttachment.encoding = encoding
            return this
        }

        fun withPath(path: String): Builder {
            this.mailAttachment.path = path
            return this
        }

        fun withSize(size: Long): Builder {
            this.mailAttachment.size = size
            return this
        }

        fun withInputStream(inputStream: InputStream): Builder {
            this.mailAttachment.inputStream = inputStream
            return this
        }

        fun build(): MailAttachment {
            return mailAttachment
        }
    }
}
