package org.teamsai.saimockbank.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saimockbank.global.jwt.JwtAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter
            jwtAuthenticationFilter;

    private final JwtAuthenticationEntryPoint
            jwtAuthenticationEntryPoint;

    @Bean
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
                                        "/link/start",
                                        "/link/select"
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