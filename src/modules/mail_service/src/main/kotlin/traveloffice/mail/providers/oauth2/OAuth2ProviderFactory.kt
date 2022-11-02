package traveloffice.mail.providers.oauth2

import traveloffice.mail.exceptions.UnSupportedProvider

class OAuth2ProviderFactory(
    googleClientId: String,
    googleClientSecret: String,
    azureClientId: String,
    azureClientSecret: String,
    redirectUrl: String
) {
    private val gmailOAuth2Provider = GmailOAuth2Provider(googleClientId, googleClientSecret, redirectUrl)
    private val outlookOAuth2Provider = OutlookOAuth2Provider(azureClientId, azureClientSecret, redirectUrl)

    fun makeProvider(type: String): OAuth2Provider {
        return when (type) {
            "gmail" -> gmailOAuth2Provider
            "office365" -> outlookOAuth2Provider
            else -> throw UnSupportedProvider("Unsupported oauth2 provider $type")
        }
    }
}
