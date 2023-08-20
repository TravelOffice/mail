package traveloffice.mail.providers

import com.google.gson.Gson
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.Config
import com.meilisearch.sdk.Settings
import com.meilisearch.sdk.model.SearchResult
import software.amazon.awssdk.services.sqs.SqsClient
import traveloffice.mail.common.HttpRequest
import traveloffice.mail.entities.search.MailMessageSearchRequest
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import traveloffice.mail.entities.search.MailSearchQueue
import traveloffice.mail.entities.search.MailSearchQueueOperation
import traveloffice.mail.entities.search.MailSearchQueueType

class SearchProvider(meiliSearchURL: String, meiliSearchKey: String, searchQueueUrl: String, region: String) {
    private var sqsClient: SqsClient
    private var searchQueueUrl: String
    private val client: Client
    private val httpClient: HttpRequest

    init {
        this.sqsClient = SqsClient.builder()
            .region(Region.of(region))
            .build()
        this.searchQueueUrl = searchQueueUrl
        val config = Config(meiliSearchURL, meiliSearchKey)
        this.client = Client(config)
        this.httpClient = HttpRequest.Builder()
            .withBaseUrl(meiliSearchURL)
            .withAuthorizationToken("Bearer $meiliSearchKey")
            .build()
    }

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
        var sendData = builder.build()
        var sendMessageSearchRequest = SendMessageRequest.builder()
                .queueUrl(this.searchQueueUrl)
                .messageBody(Gson().toJson(sendData))
                .build()
        this.sqsClient.sendMessage(sendMessageSearchRequest)
    }

    fun deleteIndex(indexUid: String) {
        var sendData = MailSearchQueue
                .Builder()
                .withType(MailSearchQueueType.INDEX)
                .withOperation(MailSearchQueueOperation.DELETE)
                .withIndexName(indexUid)
                .build()
        var sendMessageSearchRequest = SendMessageRequest.builder()
                .queueUrl(this.searchQueueUrl)
                .messageBody(Gson().toJson(sendData))
                .build()
        this.sqsClient.sendMessage(sendMessageSearchRequest)
    }

    fun search(index: String, searchRequest: MailMessageSearchRequest): SearchResult? {
        val body = Gson().toJson(searchRequest)
        return Gson().fromJson(this.httpClient.authorizationRequest("/mail/search/indexes/$index/search", "POST", body), SearchResult::class.java)
    }

    fun addDocument(index: String, document: Any) {
        var sendData = MailSearchQueue
                .Builder()
                .withType(MailSearchQueueType.DOCUMENT)
                .withOperation(MailSearchQueueOperation.CREATE)
                .withIndexName(index)
                .withDocumentData(document)
                .build()
        var sendMessageSearchRequest = SendMessageRequest.builder()
                .queueUrl(this.searchQueueUrl)
                .messageBody(Gson().toJson(sendData))
                .build()
        this.sqsClient.sendMessage(sendMessageSearchRequest)
    }

    fun addDocuments(index: String, documents: List<Any>) = documents.forEach {
        addDocument(index, it)
    }

    fun updateDocuments(index: String, documents: List<Any>) {
        documents.forEach {
            var sendData = MailSearchQueue
                    .Builder()
                    .withType(MailSearchQueueType.DOCUMENT)
                    .withOperation(MailSearchQueueOperation.UPDATE)
                    .withIndexName(index)
                    .withDocumentData(it)
                    .build()
            var sendMessageSearchRequest = SendMessageRequest.builder()
                    .queueUrl(this.searchQueueUrl)
                    .messageBody(Gson().toJson(sendData))
                    .build()
            this.sqsClient.sendMessage(sendMessageSearchRequest)
        }
    }

    fun deleteDocuments(index: String, documents: List<String>) {
        documents.forEach {
            var sendData = MailSearchQueue
                    .Builder()
                    .withType(MailSearchQueueType.DOCUMENT)
                    .withOperation(MailSearchQueueOperation.UPDATE)
                    .withIndexName(index)
                    .withDocumentId(it)
                    .build()
            var sendMessageSearchRequest = SendMessageRequest.builder()
                    .queueUrl(this.searchQueueUrl)
                    .messageBody(Gson().toJson(sendData))
                    .build()
            this.sqsClient.sendMessage(sendMessageSearchRequest)
        }
    }
}
