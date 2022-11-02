package traveloffice.mail.providers.oauth2

import com.google.gson.Gson
import okhttp3.FormBody
import okhttp3.RequestBody
import traveloffice.mail.common.HttpRequest
import traveloffice.mail.entities.oauth2.OAuth2Token
import traveloffice.mail.entities.oauth2.OAuth2User

class OutlookOAuth2Provider(
    clientId: String,
    clientSecret: String,
    redirectUri: String
) : OAuth2Provider(clientId, clientSecret, redirectUri) {
    override val authUri = "login.microsoftonline.com/common/oauth2/v2.0/authorize"
    override val tokenUri = "login.microsoftonline.com/common/oauth2/v2.0/token"
    override val userInfoUri = "graph.microsoft.com/oidc/userinfo"
    override val scopes = listOf(
        "https://outlook.office.com/IMAP.AccessAsUser.All",
        "https://outlook.office.com/SMTP.Send",
        "offline_access email openid",
        "https://graph.microsoft.com/User.Read",
    ).joinToString(" ")

    /**
     * Get OAuth user information
     * Return user information
     */
    override fun getUserInfo(codeVerifier: String, authorizationCode: String): OAuth2User {
        val oAuth2TokenForUser = requestEmailToken(codeVerifier, authorizationCode, "https://graph.microsoft.com/User.Read")
            ?: throw Exception("Could not get user information")
        val oAuth2User = getUserInfoFromToken(oAuth2TokenForUser)
        val oAuth2TokenForEmail = refreshEmailToken(oAuth2TokenForUser.refresh_token!!, "https://outlook.office.com/IMAP.AccessAsUser.All")
        oAuth2User.oAuth2Token = oAuth2TokenForEmail
        return oAuth2User
    }
    private fun requestEmailToken(codeVerifier: String, authorizationCode: String, scope: String): OAuth2Token? {
        val requestTokenBody: RequestBody = FormBody.Builder()
            .addEncoded("grant_type", "authorization_code")
            .addEncoded("client_id", clientId)
            .addEncoded("client_secret", clientSecret)
            .addEncoded("code_verifier", codeVerifier)
            .addEncoded("code", authorizationCode)
            .addEncoded("redirect_uri", redirectUri)
            .addEncoded("scope", scope)
            .build()

        val httpClient = HttpRequest.Builder()
            .withHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        return Gson().fromJson(
            httpClient.request("https://${tokenUri}", "POST", requestTokenBody),
            OAuth2Token::class.java
        )
    }

    override fun refreshEmailToken(refreshToken: String): OAuth2Token? {
        return refreshEmailToken(refreshToken, "https://outlook.office.com/IMAP.AccessAsUser.All")
    }

    private fun refreshEmailToken(refreshToken: String, scope: String): OAuth2Token? {
        val requestTokenBody: RequestBody = FormBody.Builder()
            .addEncoded("grant_type", "refresh_token")
            .addEncoded("client_id", clientId)
            .addEncoded("client_secret", clientSecret)
            .addEncoded("refresh_token", refreshToken)
            .addEncoded("scope", scope)
            .build()
        val httpClient = HttpRequest.Builder().build()
        return Gson().fromJson(
            httpClient.request("https://${tokenUri}", "POST", requestTokenBody),
            OAuth2Token::class.java
        )
    }
}
