package traveloffice.mail.providers

import com.google.gson.Gson
import com.meilisearch.sdk.Client
import com.meilisearch.sdk.Config
import com.meilisearch.sdk.Settings
import com.meilisearch.sdk.Task
import com.meilisearch.sdk.exceptions.APIError
import com.meilisearch.sdk.exceptions.MeiliSearchApiException
import com.meilisearch.sdk.exceptions.MeiliSearchException
import com.meilisearch.sdk.model.SearchResult
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import traveloffice.mail.entities.search.MailMessageSearchRequest
import java.util.concurrent.TimeUnit

class MeiliSearchProvider(meiliSearchURL: String, meiliSearchKey: String) {
    val client: Client
    private val timeout: Long = 5000
    private val jsonMediaType: MediaType = "application/json; charset=utf-8".toMediaType()
    private val customOkHttpClient: OkHttpClient

    init {
        val config = Config(meiliSearchURL, meiliSearchKey)
        this.client = Client(config)
        this.customOkHttpClient = OkHttpClient().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).build()
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

    private fun updateSetting(index: String, settings: Settings): Task? {
        val body = Gson().toJson(settings)
        return Gson().fromJson(this.patch("/indexes/$index/settings", body), Task::class.java)
    }

    fun search(index: String, searchRequest: MailMessageSearchRequest): SearchResult? {
        val body = Gson().toJson(searchRequest)
        return Gson().fromJson(this.post("/indexes/$index/search", body), SearchResult::class.java)
    }

    fun addDocuments(index: String, documents: String): Task? {
        return Gson().fromJson(this.put("/indexes/$index/documents", documents), Task::class.java)
    }

    private fun get(url: String): String? {
        return request(url, "GET")
    }

    private fun post(url: String, body: String): String? {
        return request(url, "POST", body)
    }

    private fun put(url: String, body: String): String? {
        return request(url, "PUT", body)
    }

    private fun delete(url: String, body: String): String? {
        return request(url, "PUT", body)
    }

    private fun patch(url: String, body: String): String? {
        return request(url, "PATCH", body)
    }

    private fun head(url: String): String? {
        return request(url, "HEAD")
    }

    private fun requiresRequestBody(method: String): Boolean =
        (method == "POST" || method == "PUT" || method == "PATCH")

    private fun request(url: String, httpMethod: String, body: String? = null): String? {
        val requestBuilder = Request.Builder().header("Authorization", this.client.config.bearerApiKey)
            .url(this.client.config.hostUrl + url)
        if (body == null && requiresRequestBody(httpMethod)) throw MeiliSearchException("Missing request body for method $httpMethod")
        var request = when (httpMethod) {
            "GET" -> {
                requestBuilder.get()
            }

            "POST" -> {
                requestBuilder.post(body!!.toRequestBody(jsonMediaType))
            }

            "PUT" -> {
                requestBuilder.put(body!!.toRequestBody(jsonMediaType))
            }

            "PATCH" -> {
                requestBuilder.patch(body!!.toRequestBody(jsonMediaType))
            }

            "DELETE" -> {
                requestBuilder.delete(body?.toRequestBody(jsonMediaType))
            }

            "HEAD" -> {
                requestBuilder.head()
            }

            else -> {
                throw MeiliSearchException("Unsupported method $httpMethod")
            }
        }.build()
        val call = this.customOkHttpClient.newCall(request)
        val response = call.execute()
        if (response.code >= 400) {
            throw MeiliSearchApiException(
                Gson().fromJson(response.body?.string(), APIError::class.java)
            )
        }
        return response.body?.string()
    }
}
