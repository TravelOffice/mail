package traveloffice.mail.extensions

import com.google.gson.Gson

fun Map<String, Any>.toJson(): String {
    val gson = Gson()
    return gson.toJson(this).toString()
}