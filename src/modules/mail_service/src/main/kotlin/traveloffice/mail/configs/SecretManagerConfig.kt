package traveloffice.mail.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [SecretManagerService::class])
class SecretManagerConfig {
    @Bean("SecretManager")
    fun getSecretManagerService(): SecretManagerService {
        return SecretManagerService()
    }
}