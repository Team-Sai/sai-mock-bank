
package org.teamsai.saimockbank.global.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;
import org.teamsai.saimockbank.global.security.InternalApiKeyFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${management.server.port:8082}")
    private int managementServerPort;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    @Order(0)
    @Profile("loadtest")
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(this::isPublicManagementEndpoint)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    boolean isPublicManagementEndpoint(HttpServletRequest request) {
        if (request.getLocalPort() != managementServerPort) {
            return false;
        }

        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || "/actuator/prometheus".equals(path);
    }

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiKeyFilter filter
    ) {
        FilterRegistrationBean<InternalApiKeyFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(
            HttpSecurity http,
            InternalApiKeyFilter internalApiKeyFilter
    ) throws Exception {
        http
                .securityMatcher(
                        "/api/link/confirm-key",
                        "/api/link/revoke-key",
                        "/api/link/recover-key",
                        "/api/mock-bank/link"
                )
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .addFilterBefore(
                        internalApiKeyFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(
                                jwtAuthenticationEntryPoint
                        )
                )
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(
                                        "/api/fds/admin/**",
                                        "/api/fds/evaluations",
                                        "/api/fds/evaluations/**"
                                )
                                .hasRole("ADMIN")

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/mock-bank/accounts"
                                )
                                .authenticated()

                                .requestMatchers(
                                        "/api/mock-bank/transfers/**",
                                        "/api/mock-bank/customer-accounts/**",
                                        "/api/bank-user/**",
                                        "/api/mock-bank/accounts/my/**"
                                )
                                .authenticated()

                                .requestMatchers(
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml"
                                )
                                .permitAll()

                                .requestMatchers(
                                        "/api/auth/signup",
                                        "/api/auth/login",
                                        "/api/mock-bank/**"
                                )
                                .permitAll()

                                .requestMatchers(
                                        "/",
                                        "/login",
                                        "/signup",
                                        "/home",
                                        "/link/start",
                                        "/link/select",
                                        "/transfer",
                                        "/transfer-history",
                                        "/link/identity-mismatch",
                                        "/transactions",
                                        "/link-invalid"
                                )
                                .permitAll()

                                .requestMatchers(
                                        "/css/**",
                                        "/js/**",
                                        "/img/**",
                                        "/favicon.ico",
                                        "/error"
                                )
                                .permitAll()

                                .anyRequest()
                                .authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
