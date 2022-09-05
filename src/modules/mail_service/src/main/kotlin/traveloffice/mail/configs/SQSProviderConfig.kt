package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.SQSProvider
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [SQSProvider::class])
class SQSProviderConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getSQSProvider(@Autowired secretManagerService: SecretManagerService): SQSProvider {
        val region = secretManagerService.getEnv("AWS_REGION")
        return SQSProvider(region)
    }
}