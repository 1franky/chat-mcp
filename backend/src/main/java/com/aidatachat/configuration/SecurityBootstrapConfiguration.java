package com.aidatachat.configuration;

import com.aidatachat.adapters.out.security.IdentityUserDetailsService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;

@Configuration(proxyBeanMethods = false)
public class SecurityBootstrapConfiguration {

    @Bean
    AuthenticationManager authenticationManager(
            IdentityUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${app.security.csrf-cookie-secure:false}") boolean csrfCookieSecure) {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(
                cookie -> cookie.path("/").sameSite("Lax").secure(csrfCookieSecure));
        return csrfTokens;
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CookieCsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(
                List.of(
                        new ChangeSessionIdAuthenticationStrategy(),
                        new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    CsrfLogoutHandler csrfLogoutHandler(CookieCsrfTokenRepository csrfTokens) {
        return new CsrfLogoutHandler(csrfTokens);
    }

    @Bean
    AuthenticationRateLimitFilter authenticationRateLimitFilter(
            @Value("${app.security.rate-limit.login.attempts:5}") int loginAttempts,
            @Value("${app.security.rate-limit.login.window:1m}") Duration loginWindow,
            @Value("${app.security.rate-limit.registration.attempts:10}") int registrationAttempts,
            @Value("${app.security.rate-limit.registration.window:1h}") Duration registrationWindow,
            Clock clock) {
        return new AuthenticationRateLimitFilter(
                loginAttempts, loginWindow, registrationAttempts, registrationWindow, clock);
    }

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            AuthenticationRateLimitFilter rateLimitFilter,
            CookieCsrfTokenRepository csrfTokens)
            throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception) ->
                        SecurityProblemWriter.write(
                                response,
                                401,
                                "urn:ai-data-chat:problem:unauthorized",
                                "Autenticacion requerida",
                                "Inicia sesion para continuar.");
        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) ->
                        SecurityProblemWriter.write(
                                response,
                                403,
                                "urn:ai-data-chat:problem:forbidden",
                                "Acceso denegado",
                                "No tienes permisos para realizar esta operacion.");

        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/api/system/status",
                                                "/actuator/health",
                                                "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/auth/bootstrap")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/register",
                                                "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/api/auth/me",
                                                "/api/auth/logout",
                                                "/api/providers/**",
                                                "/api/conversations/**",
                                                "/api/mcp/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfTokens)
                                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(
                        context ->
                                context.securityContextRepository(securityContextRepository)
                                        .requireExplicitSave(true))
                .sessionManagement(
                        sessions ->
                                sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                        .sessionAuthenticationStrategy(
                                                sessionAuthenticationStrategy))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
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
