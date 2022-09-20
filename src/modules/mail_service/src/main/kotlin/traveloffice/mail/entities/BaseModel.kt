package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.*
import java.util.*

@DynamoDBDocument
open class BaseModel {
    @DynamoDBHashKey(attributeName = "pk")
    lateinit var pk: String

    @DynamoDBRangeKey(attributeName = "sk")
    lateinit var sk: String

    @DynamoDBIndexRangeKey(attributeName = "lsi1", localSecondaryIndexName = "Index1")
    var lsi1: String? = null

    @DynamoDBIndexRangeKey(attributeName = "lsi2", localSecondaryIndexName = "Index2")
    var lsi2: String? = null

    @DynamoDBIndexRangeKey(attributeName = "lsi3", localSecondaryIndexName = "Index3")
    var lsi3: String? = null

    @DynamoDBIndexRangeKey(attributeName = "lsi4", localSecondaryIndexName = "Index4")
    var lsi4: String? = null

    @DynamoDBIndexRangeKey(attributeName = "lsi5", localSecondaryIndexName = "Index5")
    var lsi5: String? = null

    @DynamoDBAttribute(attributeName = "createdAt")
    var createdAt: Long = Date().time

    @DynamoDBAttribute(attributeName = "updatedAt")
    var updatedAt: Long = Date().time
}
