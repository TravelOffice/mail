package traveloffice.mail.providers.oauth2

class GmailOAuth2Provider(
    clientId: String,
    clientSecret: String,
    redirectUri: String
) : OAuth2Provider(clientId, clientSecret, redirectUri) {
    override val authUri = "accounts.google.com/o/oauth2/auth"
    override val tokenUri = "oauth2.googleapis.com/token"
    override val userInfoUri = "www.googleapis.com/oauth2/v3/userinfo"

    // Read, compose, send, and permanently delete all your email from Gmail
    override val scopes = listOf(
        "https://mail.google.com/",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile"
    ).joinToString(" ")
}
