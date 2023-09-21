package traveloffice.mail.repositories

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Repository
import traveloffice.mail.common.Constant
import traveloffice.mail.common.Constant.Companion.MAIL_ACCOUNT_INDEX
import traveloffice.mail.common.Constant.Companion.SEARCH_API_KEY_INDEX
import traveloffice.mail.common.Constant.Companion.SEARCH_INDEX
import traveloffice.mail.common.Constant.Companion.TENANT_INDEX
import traveloffice.mail.common.Constant.Companion.USER_INDEX
import traveloffice.mail.entities.MailAccount
import traveloffice.mail.entities.search.SearchApiKey
import traveloffice.mail.providers.SearchProvider
import traveloffice.mail.services.SecretManagerService
import java.util.*

/**
 * Repository for managing search-related operations and generating search tokens.
 */
@Repository
@DependsOn(value = ["SecretManager"])
class SearchRepository {
    @Autowired
    lateinit var dynamoDBMapper: DynamoDBMapper

    @Autowired
    lateinit var secretManagerService: SecretManagerService

    @Autowired
    lateinit var searchProvider: SearchProvider

    private var logger: Logger = LoggerFactory.getLogger(SearchRepository::class.java)

    /**
     * Generates a search token for the provided API key.
     * @param apiKey The API key for which to generate the search token.
     * @return The generated search token.
     */
    fun generateSearchToken(apiKey: String): String? {
        return try {
            val searchApiKey = searchProvider.getApiKey(apiKey)
            // Allow key to expire after 1 hour
            val expiresAt = Date(Date().time + 60 * 60 * 1000)
            val searchRule = mutableMapOf<String, Any>()
            searchRule["*"] = emptyMap<String, Any>()
            searchProvider.generateTenantToken(searchApiKey, searchRule, expiresAt)
        } catch (e: Exception) {
            logger.error("generateSearchToken error: ${e.message}")
            ""
        }
    }

    private fun getApiKey(tenant: String, username: String): String? {
        val pk = "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${SEARCH_INDEX}"
        val sk = SEARCH_API_KEY_INDEX
        val result = this.dynamoDBMapper.load(
            SearchApiKey::class.java, pk, sk
        ) ?: return null
        return result.apiKey
    }

    private fun createApiKey(tenant: String, username: String, apiKey: String) {
        val searchApiKey = SearchApiKey()
        searchApiKey.pk = "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${SEARCH_INDEX}"
        searchApiKey.sk = SEARCH_API_KEY_INDEX
        searchApiKey.apiKey = apiKey
        this.dynamoDBMapper.save(searchApiKey)
    }

    private fun deleteApiKey(tenant: String, username: String) {
        val searchApiKey = SearchApiKey()
        searchApiKey.pk = "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}#${SEARCH_INDEX}"
        searchApiKey.sk = SEARCH_API_KEY_INDEX
        this.dynamoDBMapper.delete(searchApiKey)
    }

    private fun getUserMailIndexes(tenant: String, username: String): List<String> {
        // Get all search indexes belonging to the user
        val pk = "${TENANT_INDEX}#${tenant}#${USER_INDEX}#${username}"
        val sk = "${MAIL_ACCOUNT_INDEX}#"
        val eav: Map<String, AttributeValue> = hashMapOf(
            ":pk" to AttributeValue().withS(pk),
            ":sk" to AttributeValue().withS(sk)
        )
        val queryExpression = DynamoDBQueryExpression<MailAccount>()
            .withKeyConditionExpression("pk = :pk and begins_with(sk, :sk)")
            .withExpressionAttributeValues(eav)

        val result = this.dynamoDBMapper.query(MailAccount::class.java, queryExpression)
        return result.map { it.searchIndex }
    }

    /**
     * Generates API keys for search operations and associates them with a user.
     * @param tenant The tenant identifier.
     * @param username The username.
     * @return The generated API key.
     */
    fun generateApiKeys(tenant: String, username: String): String? {
        // Create an API key that allows only search action
        val action = arrayOf("search")
        val description = "Allow search for tenant [$tenant] user [$username]"
        val apiKey = this.getApiKey(tenant, username)

        // Create a search API key if it does not exist or has expired
        if (apiKey != null) {
            // Check whether the key has expired or not
            val searchApiKey = searchProvider.getApiKey(apiKey)
            val currentTime = Date()
            val comparison = currentTime.compareTo(searchApiKey.expiresAt)
            if (comparison >= 0) {
                searchProvider.deleteApiKey(apiKey)
            } else {
                return apiKey
            }
        }
        // Allow the key to expire after 1 day (1 day = 24 hours * 60 minutes * 60 seconds * 1000 milliseconds)
        val expiresAt = Date(Date().time + 24 * 60 * 60 * 1000)
        val searchIndexes = this.getUserMailIndexes(tenant, username)
        val searchApiKey = searchProvider.createApiKey(action, description, searchIndexes, expiresAt)
            ?: throw Exception("Cannot create search API key for tenant [$tenant] user [$username]")
        this.createApiKey(tenant, username, searchApiKey.key)
        return searchApiKey.key
    }

    /**
     * Invalidates (deletes) the API key associated with the given tenant and username.
     * @param tenant The tenant identifier.
     * @param username The username.
     */
    fun invalidateApiKey(tenant: String, username: String) {
        return this.deleteApiKey(tenant, username)
    }
}
