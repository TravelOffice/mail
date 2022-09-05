package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import java.util.*

@DynamoDBDocument
open class BaseModel {
    lateinit var pk: String
        @DynamoDBHashKey(attributeName = "pk")
        get
    lateinit var sk: String
        @DynamoDBRangeKey(attributeName = "sk")
        get
    var createdAt: Long = Date().time
        @DynamoDBAttribute(attributeName = "createdAt")
        get
    var updatedAt: Long = Date().time
        @DynamoDBAttribute(attributeName = "updatedAt")
        get
}