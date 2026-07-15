package com.aidatachat.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityBootstrapConfiguration {

    @Bean
    SecurityFilterChain bootstrapSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/api/system/status",
                                                "/actuator/health",
                                                "/actuator/health/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }
}
