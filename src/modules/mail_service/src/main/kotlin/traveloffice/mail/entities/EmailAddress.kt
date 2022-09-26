package traveloffice.mail.entities

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.google.gson.annotations.Expose
import javax.mail.internet.InternetAddress

@DynamoDBDocument
class EmailAddress {
    @Expose
    @DynamoDBAttribute(attributeName = "address")
    lateinit var address: String

    /**
     * The personal name.
     */
    @Expose
    @DynamoDBAttribute(attributeName = "personal")
    var personal: String? = null

    fun toInternetAddress(): InternetAddress = InternetAddress(this.address, this.personal)


    class Builder {
        private val emailAddress = EmailAddress()
        fun setEmailAddress(address: String): Builder {
            this.emailAddress.address = address
            return this
        }

        fun setEmailPersonal(personal: String): Builder {
            this.emailAddress.personal = personal
            return this
        }

        fun build(): EmailAddress {
            return this.emailAddress
        }
    }
}
