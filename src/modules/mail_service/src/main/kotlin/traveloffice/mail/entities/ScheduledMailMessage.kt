package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.google.gson.annotations.Expose
import traveloffice.mail.entitiesScheduledMailStatus.ScheduledMailStatus


@DynamoDBTable(tableName = "mail_table")
class ScheduledMailMessage : MailMessage() {
    @Expose
    @DynamoDBAttribute(attributeName = "scheduledTime")
    var scheduledTime: Long? = null

    @Expose
    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = "status")
    var status: ScheduledMailStatus? = ScheduledMailStatus.ONGOING
}

