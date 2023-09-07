package traveloffice.mail.entities.search

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.google.gson.annotations.Expose
import traveloffice.mail.entities.BaseModel

@DynamoDBTable(tableName = "mail_table")
class SearchApiKey : BaseModel() {
    @Expose
    @DynamoDBAttribute(attributeName = "apiKey")
    lateinit var apiKey: String
}
