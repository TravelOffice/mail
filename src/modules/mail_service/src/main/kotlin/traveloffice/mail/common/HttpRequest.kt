package traveloffice.mail.common

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpRequest {
    private var timeout: Long = 5000
    private val jsonMediaType: MediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient =
        OkHttpClient().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).build()
    private var baseUrl: String = ""
    private var authorizationToken: String? = null
    private var headers = mutableListOf<Pair<String, String>>()

    class Builder {
        private val httpRequest = HttpRequest()

        fun withTimeOut(timeout: Long): Builder {
            this.httpRequest.timeout = timeout
            return this
        }

        fun withBaseUrl(baseUrl: String): Builder {
            this.httpRequest.baseUrl = baseUrl
            return this
        }

        fun withAuthorizationToken(token: String): Builder {
            this.httpRequest.authorizationToken = token
            return this
        }

        fun withHeader(name: String, value: String): Builder {
            this.httpRequest.headers.add(Pair(name, value))
            return this
        }

        fun build(): HttpRequest {
            return this.httpRequest
        }
    }

    private fun requiresRequestBody(method: String): Boolean =
        (method == "POST" || method == "PUT" || method == "PATCH")

    fun authorizationRequest(url: String, httpMethod: String, body: String? = null): String? {
        if (this.authorizationToken == null)
            throw Exception("Missing Authorization Token")
        return request(url, httpMethod, body, authorizationRequest = true)
    }

    fun request(url: String, httpMethod: String, body: String? = null): String? {
        return request(url, httpMethod, body)
    }

    fun request(url: String, httpMethod: String, body: RequestBody? = null): String? {
        return request(url, httpMethod, requestBody = body)
    }

    private fun request(
        url: String,
        httpMethod: String,
        body: String? = null,
        requestBody: RequestBody? = null,
        authorizationRequest: Boolean = false
    ): String? {
        val requestBuilder = Request.Builder()
        requestBuilder.url(this.baseUrl + url)
        if (this.authorizationToken != null && authorizationRequest)
            requestBuilder.header("Authorization", this.authorizationToken!!)
        this.headers.forEach {
            requestBuilder.header(it.first, it.second)
        }
        if ((body == null && requestBody == null) && requiresRequestBody(httpMethod)) throw Exception("Missing request body for method $httpMethod")
        val targetRequestBody = body?.toRequestBody(jsonMediaType) ?: requestBody
        val request = when (httpMethod) {
            "GET" -> {
                requestBuilder.get()
            }

            "POST" -> {
                requestBuilder.post(targetRequestBody!!)
            }

            "PUT" -> {
                requestBuilder.put(targetRequestBody!!)
            }

            "PATCH" -> {
                requestBuilder.patch(targetRequestBody!!)
            }

            "DELETE" -> {
                requestBuilder.delete(targetRequestBody)
            }

            "HEAD" -> {
                requestBuilder.head()
            }

            else -> {
                throw Exception("Unsupported method $httpMethod")
            }
        }.build()
        val call = this.httpClient.newCall(request)
        val response = call.execute()
        if (response.code >= 400) {
            throw Exception(response.body?.string())
        }
        return response.body?.string()
    }
}
