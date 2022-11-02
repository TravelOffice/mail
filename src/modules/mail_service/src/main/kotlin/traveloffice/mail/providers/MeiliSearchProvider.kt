package traveloffice.mail.providers

import com.google.gson.Gson
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.Config
import com.meilisearch.sdk.Settings
import com.meilisearch.sdk.Task
import com.meilisearch.sdk.exceptions.MeiliSearchApiException
import com.meilisearch.sdk.model.SearchResult
import traveloffice.mail.common.HttpRequest
import traveloffice.mail.entities.search.MailMessageSearchRequest

class MeiliSearchProvider(meiliSearchURL: String, meiliSearchKey: String) {
    private val client: Client
    private val httpClient: HttpRequest

    init {
        val config = Config(meiliSearchURL, meiliSearchKey)
        this.client = Client(config)
        this.httpClient = HttpRequest.Builder()
            .withBaseUrl(meiliSearchURL)
            .withAuthorizationToken("Bearer $meiliSearchKey")
            .build()
    }

    fun createOrUpdateIndex(indexUid: String, settings: Settings): Task? {
        try {
            this.client.index(indexUid).settings
        } catch (e: MeiliSearchApiException) {
            if (e.errorCode == "index_not_found") {
                // Create new index
                this.client.createIndex(indexUid, "id")
                return this.updateSetting(indexUid, settings)
            }
            throw e
        }
        // Update setting if needed
        return this.updateSetting(indexUid, settings)
    }

    fun deleteIndex(indexUid: String): Task? {
        return Gson().fromJson(this.httpClient.authorizationRequest("/indexes/$indexUid", "GET"), Task::class.java)
    }

    private fun updateSetting(index: String, settings: Settings): Task? {
        val body = Gson().toJson(settings)
        return Gson().fromJson(this.httpClient.authorizationRequest("/indexes/$index/settings", "PATCH", body), Task::class.java)
    }

    fun search(index: String, searchRequest: MailMessageSearchRequest): SearchResult? {
        val body = Gson().toJson(searchRequest)
        return Gson().fromJson(this.httpClient.authorizationRequest("/indexes/$index/search", "POST", body), SearchResult::class.java)
    }

    fun addDocuments(index: String, documents: String): Task? {
        return Gson().fromJson(this.httpClient.authorizationRequest("/indexes/$index/documents", "POST", documents), Task::class.java)
    }

    fun updateDocuments(index: String, documents: String): Task? {
        return Gson().fromJson(this.httpClient.authorizationRequest("/indexes/$index/documents", "PUT", documents), Task::class.java)
    }

    fun deleteDocuments(index: String, documents: String): Task? {
        return Gson().fromJson(
            this.httpClient.authorizationRequest("/indexes/$index/documents/delete-batch", "POST", documents),
            Task::class.java
        )
    }
}
