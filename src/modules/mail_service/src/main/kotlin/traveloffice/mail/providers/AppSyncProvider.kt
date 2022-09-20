package traveloffice.mail.providers

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import traveloffice.mail.entities.qgl.SendNotificationMutation
import java.util.concurrent.TimeUnit

class AppSyncProvider(private val appSyncEndpoint: String, private val appSyncAPIKey: String) {
    private val timeout: Long = 5000
    private val jsonMediaType: MediaType = "application/json; charset=utf-8".toMediaType()
    private var logger: Logger = LoggerFactory.getLogger(AppSyncProvider::class.java)

    /**
     * This method perform to notification new email received
     *
     * @param notificationType: The notification type pass to consumer.
     * @param data: The notification data.
     * @param timestamp: The time which notification sent.
     * @param tenant: The tenant name
     * @param username: The user receive the notification
     *
     * @return The path of html email body which saved to the local storage
     * @exception
     */
    fun executeSendNotification(
        notificationType: String,
        data: String,
        timestamp: Long,
        tenant: String?,
        username: String?,
    ): ResponseBody? {
        val client = OkHttpClient().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).build()
        // Args
        val args = mapOf(
            "tenant" to tenant,
            "username" to username,
            "type" to notificationType,
            "data" to data,
            "timestamp" to timestamp,
        )
        val notificationData = mapOf(
            "query" to SendNotificationMutation.sendNotificationGql,
            "variables" to args,
            "operationName" to "SendNotificationMutation"
        )
        val body: RequestBody = Gson().toJson(notificationData).toRequestBody(jsonMediaType)
        val request: Request = Request.Builder()
            .url(this.appSyncEndpoint)
            .post(body)
            .header("x-api-key", this.appSyncAPIKey)
            .build()

        client.newCall(request).execute().also {
            logger.info("[AppSyncProvider.executeSendNotification] Fetched mutation data ${it.body?.string()}")
            return it.body
        }
    }
}
