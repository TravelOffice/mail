package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.S3Provider
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [S3Provider::class])
class S3ProviderConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getS3Provider(@Autowired secretManagerService: SecretManagerService): S3Provider {
        val region = secretManagerService.getEnv("AWS_REGION")
        return S3Provider(region)
    }
}
