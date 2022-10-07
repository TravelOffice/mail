package traveloffice.mail.entities

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose

data class MailMessageMetaData(
    @Expose
    var to: List<EmailAddress>,

    @Expose
    var cc: List<EmailAddress>? = null,

    @Expose
    var bcc: List<EmailAddress>? = null,
) {
    fun toJson(): String {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        return gson.toJson(this)
    }
}
