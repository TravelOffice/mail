package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.AppSyncProvider
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [AppSyncProvider::class])
class AppSyncProviderConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getAppSyncProvider(@Autowired secretManagerService: SecretManagerService): AppSyncProvider {
        val appSyncEndpoint = secretManagerService.getAWSSecretValue("CHAT_APPSYNC_GRAPHQL_ENDPOINT")
        val appSyncAPIKey = secretManagerService.getAWSSecretValue("CHAT_APPSYNC_API_KEY")
        return AppSyncProvider(appSyncEndpoint, appSyncAPIKey)
    }
}
