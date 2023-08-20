package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.SearchProvider
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [SearchProvider::class])
class SearchConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getSearchProvider(@Autowired secretManagerService: SecretManagerService): SearchProvider {
        val searchQueueUrl = secretManagerService.getEnv("MAIL_SEARCH_QUEUE_URL")
        val region = secretManagerService.getEnv("AWS_REGION")
        val searchURL = secretManagerService.getAWSSecretValue("SEARCH_ENGINE_ENDPOINT")
        val searchKey = secretManagerService.getAWSSecretValue("SEARCH_ENGINE_MASTER_KEY")
        return SearchProvider(searchURL, searchKey, searchQueueUrl, region)
    }
}
