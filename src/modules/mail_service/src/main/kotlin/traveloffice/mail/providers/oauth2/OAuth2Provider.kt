package traveloffice.mail.providers.oauth2

import com.google.gson.Gson
import okhttp3.FormBody
import okhttp3.RequestBody
import org.apache.commons.lang.time.DateUtils
import org.apache.http.client.utils.URIBuilder
import traveloffice.mail.common.HttpRequest
import traveloffice.mail.entities.oauth2.CodeVerifier
import traveloffice.mail.entities.oauth2.OAuth2Token
import traveloffice.mail.entities.oauth2.OAuth2User
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*


abstract class OAuth2Provider(
    protected val clientId: String,
    protected val clientSecret: String,
    protected val redirectUri: String
) {
    abstract val authUri: String
    abstract val tokenUri: String
    abstract val userInfoUri: String
    abstract val scopes: String

    /**
     * Get OAuth user information
     * Return user information
     */
    open fun getUserInfo(codeVerifier: String, authorizationCode: String): OAuth2User {
        val oAuth2Token = requestEmailToken(codeVerifier, authorizationCode)
            ?: throw Exception("Could not get user information")
        val oAuth2User = getUserInfoFromToken(oAuth2Token)
        oAuth2User.oAuth2Token = oAuth2Token
        return oAuth2User
    }

    protected fun getUserInfoFromToken(oAuth2Token: OAuth2Token): OAuth2User {
        val httpClient = HttpRequest.Builder()
            .withAuthorizationToken("Bearer ${oAuth2Token.access_token}")
            .build()
        return Gson().fromJson(
            httpClient.authorizationRequest("https://$userInfoUri", "GET"),
            OAuth2User::class.java
        )
    }

    /**
     * Generate OAuth login url from [codeChallenge] [state] and [mail]
     * Return OAuth login url
     */
    open fun generateOAuth2LoginUrl(codeChallenge: String, state: String, mail: String): URL {
        val builder = URIBuilder()
        builder.scheme = "https"
        builder.host = authUri
        builder.addParameter("response_type", "code")
        builder.addParameter("code_challenge", codeChallenge)
        builder.addParameter("code_challenge_method", "S256")
        builder.addParameter("client_id", clientId)
        if (scopes.isNotEmpty())
            builder.addParameter("scope", scopes)
        builder.addParameter("state", state)
        builder.addParameter("redirect_uri", redirectUri)
        builder.addParameter("access_type", "offline")
        builder.addParameter("prompt", "consent")
        builder.addParameter("login_hint", mail)
        builder.addParameter("include_granted_scopes", "true")
        return builder.build().toURL()
    }

    /**
     * Generate base 64 encoded random string
     * Return code verifier
     */
    private fun generateRandomString(): String {
        val sr = SecureRandom()
        val code = ByteArray(32)
        sr.nextBytes(code)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code)
            ?: throw Exception("Cannot generate random string")
    }

    /**
     * Generate code verifier, code challenge, state to create OAuth login url for [provider]
     * Return code verifier
     */
    fun generateCodeChallenge(provider: String): CodeVerifier {
        val codeVerifier = generateRandomString()
        val bytes = codeVerifier.toByteArray(charset("US-ASCII"))
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, bytes.size)
        val digest: ByteArray = md.digest()
        val codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            ?: throw Exception("Cannot generate code challenge")
        // Create random UID state
        val state = UUID.randomUUID().toString()
        // Keep the code challenge still alive within 3 minutes
        val expDate = DateUtils.addMinutes(Date(), 3).time.div(1000L)
        return CodeVerifier.Builder()
            .withProvider(provider)
            .withCodeChallenge(codeChallenge)
            .withState(state)
            .withCodeVerifier(codeVerifier)
            .withTTL(expDate)
            .build()
    }

    open fun requestEmailToken(codeVerifier: String, authorizationCode: String): OAuth2Token? {
        val requestTokenBody: RequestBody = FormBody.Builder()
            .addEncoded("grant_type", "authorization_code")
            .addEncoded("client_id", clientId)
            .addEncoded("client_secret", clientSecret)
            .addEncoded("code_verifier", codeVerifier)
            .addEncoded("code", authorizationCode)
            .addEncoded("redirect_uri", redirectUri)
            .build()

        val httpClient = HttpRequest.Builder()
            .withHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        return Gson().fromJson(
            httpClient.request("https://${tokenUri}", "POST", requestTokenBody),
            OAuth2Token::class.java
        )
    }

    open fun refreshEmailToken(refreshToken: String): OAuth2Token? {
        val requestTokenBody: RequestBody = FormBody.Builder()
            .addEncoded("grant_type", "refresh_token")
            .addEncoded("client_id", clientId)
            .addEncoded("refresh_token", refreshToken)
            .build()
        val httpClient = HttpRequest.Builder().build()
        return Gson().fromJson(
            httpClient.request("https://${tokenUri}", "POST", requestTokenBody),
            OAuth2Token::class.java
        )
    }
}
