package traveloffice.mail.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import traveloffice.mail.providers.IMAPProvider

@Configuration
@ComponentScan(basePackageClasses = [IMAPProvider::class])
class IMAPProviderConfig {
    @Bean
    fun getIMAPProvider(): IMAPProvider {
        return IMAPProvider()
    }
}