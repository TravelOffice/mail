package traveloffice.mail.entities.oauth2

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import traveloffice.mail.common.Constant.Companion.CODE_VERIFIER_INDEX
import traveloffice.mail.entities.BaseModel

@DynamoDBTable(tableName = "mail_table")
class CodeVerifier : BaseModel() {
    val state: String
        @DynamoDBIgnore
        get() = this.sk

    @DynamoDBAttribute(attributeName = "codeVerifier")
    lateinit var code: String

    @DynamoDBAttribute(attributeName = "provider")
    lateinit var provider: String

    @DynamoDBIgnore
    var codeChallenge: String = ""

    class Builder {
        private val codeVerifier = CodeVerifier()

        fun withProvider(provider: String): Builder {
            this.codeVerifier.provider = provider
            return this
        }

        fun withCodeChallenge(codeChallenge: String): Builder {
            this.codeVerifier.codeChallenge = codeChallenge
            return this
        }

        fun withState(state: String): Builder {
            this.codeVerifier.sk = state
            return this
        }

        fun withCodeVerifier(codeVerifier: String): Builder {
            this.codeVerifier.code = codeVerifier
            return this
        }

        fun withTTL(ttl: Long): Builder {
            this.codeVerifier.ttl = ttl
            return this
        }

        fun build(): CodeVerifier {
            return codeVerifier
        }
    }
}
