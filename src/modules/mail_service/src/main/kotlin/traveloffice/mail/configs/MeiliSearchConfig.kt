package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.MeiliSearchProvider
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [MeiliSearchProvider::class])
class MeiliSearchConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getMeiliSearchProvider(@Autowired secretManagerService: SecretManagerService): MeiliSearchProvider {
        val meiliSearchURL = secretManagerService.getAWSSecretValue("SEARCH_ENGINE_ENDPOINT")
        val meiliSearchKey = secretManagerService.getAWSSecretValue("SEARCH_ENGINE_MASTER_KEY")
        return MeiliSearchProvider(meiliSearchURL, meiliSearchKey)
    }
}
