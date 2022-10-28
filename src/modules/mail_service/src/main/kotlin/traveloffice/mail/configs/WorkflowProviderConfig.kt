package traveloffice.mail.configs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import traveloffice.mail.providers.WorkflowProvider
import traveloffice.mail.services.SecretManagerService

@Configuration
@ComponentScan(basePackageClasses = [WorkflowProvider::class])
class WorkflowProviderConfig {
    @Bean
    @DependsOn("SecretManager")
    fun getSfnProvider(@Autowired secretManagerService: SecretManagerService): WorkflowProvider {
        val region = secretManagerService.getEnv("AWS_REGION")

        return WorkflowProvider(region)
    }
}