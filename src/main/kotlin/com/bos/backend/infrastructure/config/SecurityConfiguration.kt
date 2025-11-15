package com.bos.backend.infrastructure.config

import com.bos.backend.infrastructure.security.JwtSecurityContextRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration(
    private val jwtSecurityContextRepository: JwtSecurityContextRepository,
    private val pushTestProperties: PushTestProperties,
) {
    @Bean
    fun pushTestUserDetailsService(): MapReactiveUserDetailsService {
        val user =
            User
                .withUsername(pushTestProperties.username)
                .password("{noop}${pushTestProperties.password}")
                .roles("PUSH_TEST")
                .build()
        return MapReactiveUserDetailsService(user)
    }

    @Bean
    fun securityFilterChain(
        http: ServerHttpSecurity,
        corsConfig: CorsConfigurationSource,
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .cors { it.configurationSource(corsConfig) }
            .authorizeExchange {
                it
                    .pathMatchers(
                        "/auth/sign-up",
                        "/auth/sign-in",
                        "/auth/email-verification/**",
                        "/auth/password-reset",
                        "/auth/check-email",
                        "/auth/token/refresh",
                        "/api/**",
                        "/actuator/**",
                        "/push/test/**",
                        "/transactions/*/share",
                    ).permitAll()
                    .pathMatchers("/admin/**").hasRole("PUSH_TEST")
                    .anyExchange()
                    .authenticated()
            }.securityContextRepository(jwtSecurityContextRepository)
            .build()

    @Bean
    fun corsConfig(corsProperties: CorsProperties): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = corsProperties.allowedMethods
                allowedHeaders = corsProperties.allowedHeaders
                allowedOriginPatterns = corsProperties.allowedOriginPatterns
                allowCredentials = true
            }

        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}

@Component
@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    var allowedOrigins: List<String> = listOf(),
    var allowedMethods: List<String> = listOf(),
    var allowedHeaders: List<String> = listOf(),
    var allowedOriginPatterns: List<String> = listOf(),
)

@Component
@ConfigurationProperties(prefix = "push.test.auth")
data class PushTestProperties(
    var username: String = "",
    var password: String = "",
)
