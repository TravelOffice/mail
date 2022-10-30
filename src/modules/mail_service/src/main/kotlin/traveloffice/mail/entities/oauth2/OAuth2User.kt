package traveloffice.mail.entities.oauth2

data class OAuth2User(
    val email: String,
    val name: String,
    var oAuth2Token: OAuth2Token? = null
)
