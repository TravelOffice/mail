package traveloffice.mail.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain


@Profile("mail_api")
@EnableWebSecurity // Enable Spring Security's web
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class SecurityConfig {
    /**
     * Defines a filter chain which is capable of being matched against an HttpServletRequest
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        // We have made each endpoint accessible by Oath 2.0 authenticated requests
        http.cors().and().csrf().disable().authorizeRequests {
            it.antMatchers("/mail/healthcheck").permitAll().anyRequest().authenticated()
        }.oauth2ResourceServer().jwt()
        return http.build()
    }
}
