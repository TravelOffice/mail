package traveloffice.mail.entities.oauth2

data class EmailServerData(
    var type: String,
    var host: String,
    var port: Int,
    var connection_security: String,
    var authentication_method: String,
    var username_login: Boolean? = null
)
