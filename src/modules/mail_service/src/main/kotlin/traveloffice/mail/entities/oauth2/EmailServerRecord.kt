package traveloffice.mail.entities.oauth2

import com.google.gson.Gson

data class EmailServerRecord(
    var name: String,
    var provider: String,
    var domains: List<String>,
    var incoming: EmailServerData,
    var outgoing: EmailServerData,
    var oauthUrl: String? = null
) {
    fun toMap(): MutableMap<String, Any> {
        val gson = Gson()
        return gson
            .fromJson<Map<String, Any>>(gson.toJson(this), MutableMap::class.java)
            .toMutableMap()
    }
}
