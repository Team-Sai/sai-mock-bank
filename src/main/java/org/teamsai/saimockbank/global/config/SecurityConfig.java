package org.teamsai.saimockbank.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

    private final JwtAuthenticationFilter
            jwtAuthenticationFilter;

    private final JwtAuthenticationEntryPoint
            jwtAuthenticationEntryPoint;

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(
            HttpSecurity http,
            InternalApiKeyFilter internalApiKeyFilter
    ) throws Exception {
        http.securityMatcher("/api/link/confirm-key")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
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

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                jwtAuthenticationEntryPoint
                        )
                )

                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(
                                        "/api/mock-bank/transfers/**",
                                        "/api/mock-bank/customer-accounts/**",
                                        "/api/bank-user/**",
                                        "/api/mock-bank/accounts/my"
                                )
                                .authenticated()

                                .requestMatchers(
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml"
                                ).permitAll()
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
                                        "/link/invalid"
                                )
                                .permitAll()

                                .requestMatchers(
                                        "/css/**",
                                        "/js/**",
                                        "/images/**",
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