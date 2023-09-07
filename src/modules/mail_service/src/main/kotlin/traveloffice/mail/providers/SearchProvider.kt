package traveloffice.mail.providers

import com.google.gson.Gson
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.Config
import com.meilisearch.sdk.TenantTokenOptions
import com.meilisearch.sdk.model.Key
import com.meilisearch.sdk.model.SearchResult
import com.meilisearch.sdk.model.Settings
import software.amazon.awssdk.services.sqs.SqsClient
import traveloffice.mail.common.HttpRequest
import traveloffice.mail.entities.search.MailMessageSearchRequest
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import traveloffice.mail.entities.search.MailSearchQueue
import traveloffice.mail.entities.search.MailSearchQueueOperation
import traveloffice.mail.entities.search.MailSearchQueueType
import java.util.Date

/**
 * A provider for performing search operations using MeiliSearch and managing search queues using AWS SQS.
 * @param meiliSearchURL The URL of the MeiliSearch server.
 * @param meiliSearchKey The MeiliSearch API key.
 * @param searchQueueUrl The URL of the AWS SQS queue.
 * @param region The AWS region for SQS.
 */
class SearchProvider(
    meiliSearchURL: String,
    meiliSearchKey: String,
    searchQueueUrl: String,
    region: String
) {
    private var sqsClient: SqsClient
    private var searchQueueUrl: String
    private val client: Client
    private val httpClient: HttpRequest

    init {
        // Initialize AWS SQS client
        this.sqsClient = SqsClient.builder()
            .region(Region.of(region))
            .build()
        this.searchQueueUrl = searchQueueUrl

        // Initialize MeiliSearch client and HTTP client
        val config = Config(meiliSearchURL, meiliSearchKey)
        this.client = Client(config)
        this.httpClient = HttpRequest.Builder()
            .withBaseUrl(meiliSearchURL)
            .withAuthorizationToken("Bearer $meiliSearchKey")
            .build()
    }

    /**
     * Create a MeiliSearch index.
     * @param indexUid The unique identifier for the index.
     * @param settings The settings for the index.
     * @param primaryKey The primary key for the index.
     */
    fun createIndex(indexUid: String, settings: Settings, primaryKey: String? = null) {
        var builder = MailSearchQueue
            .Builder()
            .withType(MailSearchQueueType.INDEX)
            .withOperation(MailSearchQueueOperation.CREATE)
            .withIndexName(indexUid)
            .withIndexSettings(settings)
        if (primaryKey != null) {
            builder = builder.withPrimaryKey(primaryKey)
        }
        val sendData = builder.build()
        val sendMessageSearchRequest = SendMessageRequest.builder()
            .queueUrl(this.searchQueueUrl)
            .messageBody(Gson().toJson(sendData))
            .build()
        this.sqsClient.sendMessage(sendMessageSearchRequest)
    }

    /**
     * Delete a MeiliSearch index.
     * @param indexUid The unique identifier for the index to delete.
     */
    fun deleteIndex(indexUid: String) {
        val sendData = MailSearchQueue
            .Builder()
            .withType(MailSearchQueueType.INDEX)
            .withOperation(MailSearchQueueOperation.DELETE)
            .withIndexName(indexUid)
            .build()
        val sendMessageSearchRequest = SendMessageRequest.builder()
            .queueUrl(this.searchQueueUrl)
            .messageBody(Gson().toJson(sendData))
            .build()
        this.sqsClient.sendMessage(sendMessageSearchRequest)
    }

    /**
     * Search for documents in a MeiliSearch index.
     * @param index The name of the index to search in.
     * @param searchRequest The search request containing search criteria.
     * @return A search result containing matching documents.
     */
    fun search(index: String, searchRequest: MailMessageSearchRequest): SearchResult? {
        val body = Gson().toJson(searchRequest)
        return Gson().fromJson(
            this.httpClient.authorizationRequest("/mail/search/indexes/$index/search", "POST", body),
            SearchResult::class.java
        )
    }

    /**
     * Add a document to a MeiliSearch index.
     * @param index The name of the index to add the document to.
     * @param document The document to add.
     */
    fun addDocument(index: String, document: Any) {
        val sendData = MailSearchQueue
            .Builder()
            .withType(MailSearchQueueType.DOCUMENT)
            .withOperation(MailSearchQueueOperation.CREATE)
            .withIndexName(index)
            .withDocumentData(document)
            .build()
        val sendMessageSearchRequest = SendMessageRequest.builder()
            .queueUrl(this.searchQueueUrl)
            .messageBody(Gson().toJson(sendData))
            .build()
        this.sqsClient.sendMessage(sendMessageSearchRequest)
    }

    /**
     * Add multiple documents to a MeiliSearch index.
     * @param index The name of the index to add the documents to.
     * @param documents The list of documents to add.
     */
    fun addDocuments(index: String, documents: List<Any>) = documents.forEach {
        addDocument(index, it)
    }

    /**
     * Update multiple documents in a MeiliSearch index.
     * @param index The name of the index to update the documents in.
     * @param documents The list of documents to update.
     */
    fun updateDocuments(index: String, documents: List<Any>) {
        documents.forEach {
            val sendData = MailSearchQueue
                .Builder()
                .withType(MailSearchQueueType.DOCUMENT)
                .withOperation(MailSearchQueueOperation.UPDATE)
                .withIndexName(index)
                .withDocumentData(it)
                .build()
            val sendMessageSearchRequest = SendMessageRequest.builder()
                .queueUrl(this.searchQueueUrl)
                .messageBody(Gson().toJson(sendData))
                .build()
            this.sqsClient.sendMessage(sendMessageSearchRequest)
        }
    }

    /**
     * Delete multiple documents from a MeiliSearch index.
     * @param index The name of the index to delete the documents from.
     * @param documents The list of document IDs to delete.
     */
    fun deleteDocuments(index: String, documents: List<String>) {
        documents.forEach {
            val sendData = MailSearchQueue
                .Builder()
                .withType(MailSearchQueueType.DOCUMENT)
                .withOperation(MailSearchQueueOperation.UPDATE)
                .withIndexName(index)
                .withDocumentId(it)
                .build()
            val sendMessageSearchRequest = SendMessageRequest.builder()
                .queueUrl(this.searchQueueUrl)
                .messageBody(Gson().toJson(sendData))
                .build()
            this.sqsClient.sendMessage(sendMessageSearchRequest)
        }
    }

    /**
     * Get a MeiliSearch API key.
     * @param apiKey The API key to retrieve.
     * @return The retrieved API key.
     */
    fun getApiKey(apiKey: String): Key {
        return this.client.getKey(apiKey)
    }

    /**
     * Create a MeiliSearch API key.
     * @param actions The actions allowed by the API key.
     * @param description A description for the API key.
     * @param indexes The list of indexes that the API key can access.
     * @param expiresAt The expiration date and time of the API key.
     * @return The created API key.
     */
    fun createApiKey(actions: Array<String>, description: String, indexes: List<String>, expiresAt: Date): Key? {
        val keyOption = Key()
        keyOption.actions = actions
        keyOption.description = description
        keyOption.indexes = indexes.toTypedArray()
        keyOption.expiresAt = expiresAt
        return this.client.createKey(keyOption)
    }

    /**
     * Delete a MeiliSearch API key.
     * @param apiKey The API key to delete.
     */
    fun deleteApiKey(apiKey: String) {
        return this.client.deleteKey(apiKey)
    }

    /**
     * Generate a MeiliSearch tenant token.
     * @param key The API key to associate with the tenant token.
     * @param searchRule The search rule for the tenant token.
     * @param expiresAt The expiration date and time of the tenant token.
     * @return The generated tenant token.
     */
    fun generateTenantToken(key: Key, searchRule: MutableMap<String, Any>, expiresAt: Date): String? {
        val options = TenantTokenOptions()
        options.expiresAt = expiresAt
        options.apiKey = key.key
        return this.client.generateTenantToken(key.uid, searchRule, options)
    }
}
