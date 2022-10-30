package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.oauth2.OAuth2ProviderFactory
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [OAuth2ProviderFactory::class])
class OAuth2ProviderConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getOAuth2Provider(@Autowired secretManagerService: SecretManagerService): OAuth2ProviderFactory {
        val googleClientId = secretManagerService.getEnv("MAIL_GOOGLE_CLIENT_ID")
        val googleClientSecret = secretManagerService.getEnv("MAIL_GOOGLE_CLIENT_SECRET")
        val azureClientId = secretManagerService.getEnv("MAIL_AZURE_CLIENT_ID")
        val azureClientSecret = secretManagerService.getEnv("MAIL_AZURE_CLIENT_SECRET")
        val redirectUrl = secretManagerService.getEnv("MAIL_OAUTH2_REDIRECT_ENDPOINT")
        return OAuth2ProviderFactory(
            googleClientId, googleClientSecret,
            azureClientId, azureClientSecret,
            redirectUrl
        )
    }
}
