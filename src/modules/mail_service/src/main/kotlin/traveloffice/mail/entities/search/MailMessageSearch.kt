package traveloffice.mail.entities.search

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.meilisearch.sdk.Settings
import traveloffice.mail.entities.EmailAddress

data class MailMessageSearch(
    @Expose
    var id: String,
    @Expose
    var messageId: String,
    @Expose
    var box: String,
    @Expose
    var from: List<EmailAddress>,
    @Expose
    var to: List<EmailAddress>,
    @Expose
    var cc: List<EmailAddress>?,
    @Expose
    var bcc: List<EmailAddress>?,
    @Expose
    var subject: String?,
    @Expose
    var content: String?,
    @Expose
    var attachment: List<String>?,
    @Expose
    var date: Long?,
    @Expose
    var flags: List<String>,
    @Expose
    var ttl: Long? = null,
) {
    data class IndexConfig(
        var displayedAttributes: ArrayList<String> = arrayListOf("*"),
        var searchableAttributes: ArrayList<String> = arrayListOf(
            "from",
            "to",
            "cc",
            "bcc",
            "subject",
            "content",
            "attachments"
        ),
        var filterableAttributes: ArrayList<String> = arrayListOf("box", "flags", "from.address", "ttl"),
        var sortableAttributes: ArrayList<String> = arrayListOf("date"),
    ) {
        fun defaultSetting(): Settings {
            val settings = Settings()
            settings.displayedAttributes = displayedAttributes.toTypedArray()
            settings.searchableAttributes = searchableAttributes.toTypedArray()
            settings.filterableAttributes = filterableAttributes.toTypedArray()
            settings.sortableAttributes = sortableAttributes.toTypedArray()
            return settings
        }
    }

    fun toJsonString(): String {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        return gson.toJson(this)
    }
}
