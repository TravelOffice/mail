package traveloffice.mail.entities.oauth2

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument

@DynamoDBDocument
class OAuth2Token {
    @DynamoDBAttribute(attributeName = "access_token")
    lateinit var access_token: String

    @DynamoDBAttribute(attributeName = "id_token")
    var id_token: String? = null

    @DynamoDBAttribute(attributeName = "refresh_token")
    var refresh_token: String? = null

    @DynamoDBAttribute(attributeName = "token_type")
    lateinit var token_type: String

    @DynamoDBAttribute(attributeName = "scope")
    var scope: String? = null

    @DynamoDBAttribute(attributeName = "expires_in")
    var expires_in: Long = 0
}
