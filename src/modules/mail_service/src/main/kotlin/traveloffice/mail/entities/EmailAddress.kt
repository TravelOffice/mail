package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument

@DynamoDBDocument
class EmailAddress {
    @DynamoDBAttribute(attributeName = "address")
    lateinit var address: String

    /**
     * The personal name.
     */
    @DynamoDBAttribute(attributeName = "personal")
    var personal: String? = null
}
