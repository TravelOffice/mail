package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.google.gson.annotations.Expose
import traveloffice.mail.common.Constant.Companion.MAX_BRIEF_ADDRESS_SIZE

@DynamoDBDocument
class BriefAddress {
    @Expose
    @DynamoDBAttribute(attributeName = "addresses")
    var addresses: List<EmailAddress> = emptyList()

    @DynamoDBIgnore
    var rawAddress: List<EmailAddress> = emptyList()

    @Expose
    @DynamoDBAttribute(attributeName = "count")
    var count: Int = 0

    class Builder {
        private val briefAddress = BriefAddress()

        fun withAddresses(addresses: List<EmailAddress>): Builder {
            this.briefAddress.rawAddress = addresses
            this.briefAddress.addresses = addresses.subList(0, addresses.size.coerceAtMost(MAX_BRIEF_ADDRESS_SIZE))
            this.briefAddress.count = addresses.size
            return this
        }

        fun build(): BriefAddress {
            return this.briefAddress
        }
    }
}
